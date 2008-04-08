package org.jetbrains.plugins.groovy.compiler.generator;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrConstructor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.util.containers.CharTrie;

import java.io.*;
import java.util.*;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 03.05.2007
 */
public class GroovyToJavaGenerator implements SourceGeneratingCompiler, CompilationStatusListener {
  private static final Map<String, String> typesToInitialValues = new HashMap<String, String>();
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.compiler.generator.GroovyToJavaGenerator");

  static {
    typesToInitialValues.put("boolean", "false");
    typesToInitialValues.put("int", "0");
    typesToInitialValues.put("short", "0");
    typesToInitialValues.put("long", "0L");
    typesToInitialValues.put("byte", "0");
    typesToInitialValues.put("char", "'c'");
    typesToInitialValues.put("double", "0D");
    typesToInitialValues.put("float", "0F");
    typesToInitialValues.put("void", "");
  }

  private static final String[] JAVA_MODIFIERS = new String[]{
      PsiModifier.PUBLIC,
      PsiModifier.PROTECTED,
      PsiModifier.PRIVATE,
      PsiModifier.PACKAGE_LOCAL,
      PsiModifier.STATIC,
      PsiModifier.ABSTRACT,
      PsiModifier.FINAL,
      PsiModifier.NATIVE,
      PsiModifier.SYNCHRONIZED,
      PsiModifier.STRICTFP,
      PsiModifier.TRANSIENT,
      PsiModifier.VOLATILE
  };

  private static final String[] JAVA_TYPE_DEFINITION_MODIFIERS = new String[]{
      PsiModifier.PUBLIC,
      PsiModifier.ABSTRACT,
      PsiModifier.FINAL
  };

  private static final CharSequence PREFIX_SEPARATOR = "/";
  private CompileContext myContext;
  private Project myProject;

  public GroovyToJavaGenerator(Project project) {
    myProject = project;
  }

  public GenerationItem[] getGenerationItems(CompileContext context) {
    myContext = context;

    List<GenerationItem> generationItems = new ArrayList<GenerationItem>();
    GenerationItem item;
    for (VirtualFile file : getGroovyFilesToGenerate(context)) {
      if (CompilerManager.getInstance(myProject).isExcludedFromCompilation(file)) continue;

      final GroovyFileBase psiFile = findPsiFile(file);
      boolean isInTestSources = ModuleRootManager.getInstance(getModuleByFile(context, file)).getFileIndex().isInTestSourceContent(file);

      GrTopStatement[] statements = getTopStatementsInReadAction(psiFile);

      boolean needCreateTopLevelClass = !needsCreateClassFromFileName(statements);

      String prefix = "";
      if (statements.length > 0 && statements[0] instanceof GrPackageDefinition) {
        prefix = getJavaClassPackage((GrPackageDefinition) statements[0]);
      }

      final Module module = getModuleByFile(context, file);

      //top level class
      if (needCreateTopLevelClass) {
        generationItems.add(new GenerationItemImpl(prefix + file.getNameWithoutExtension() + "." + "java", module, new TimestampValidityState(file.getTimeStamp()), isInTestSources, file));
      }

      GrTypeDefinition[] typeDefinitions = ApplicationManager.getApplication().runReadAction(new Computable<GrTypeDefinition[]>() {
        public GrTypeDefinition[] compute() {
          return psiFile.getTypeDefinitions();
        }
      });

      for (GrTypeDefinition typeDefinition : typeDefinitions) {
        item = new GenerationItemImpl(prefix + typeDefinition.getName() + "." + "java", module, new TimestampValidityState(file.getTimeStamp()), isInTestSources, file);
        generationItems.add(item);
      }
    }
    return generationItems.toArray(new GenerationItem[generationItems.size()]);
  }

  protected Module getModuleByFile(CompileContext context, VirtualFile file) {
    return context.getModuleByFile(file);
  }

  protected VirtualFile[] getGroovyFilesToGenerate(CompileContext context) {
    return context.getProjectCompileScope().getFiles(GroovyFileType.GROOVY_FILE_TYPE, true);
  }

  public GenerationItem[] generate(CompileContext context, GenerationItem[] itemsToGenerate, VirtualFile outputRootDirectory) {
    List<GenerationItem> generatedItems = new ArrayList<GenerationItem>();
    Map<String, GenerationItem> pathsToItemsMap = new HashMap<String, GenerationItem>();

    //puts items witch can be generated
    for (GenerationItem item : itemsToGenerate) {
      pathsToItemsMap.put(item.getPath(), item);
    }

    Set<VirtualFile> vFiles = new HashSet<VirtualFile>();
    for (GenerationItem item : itemsToGenerate) {
      vFiles.add(((GenerationItemImpl) item).getVFile());
    }

    for (VirtualFile vFile : vFiles) {
      //generate java classes form groovy source files
      List<String> generatedJavaFilesRelPaths = generateItems(vFile, outputRootDirectory);
      for (String relPath : generatedJavaFilesRelPaths) {
        GenerationItem generationItem = pathsToItemsMap.get(relPath);
        if (generationItem != null)
          generatedItems.add(generationItem);
      }
    }

    return generatedItems.toArray(new GenerationItem[generatedItems.size()]);
  }

  private GroovyFile findPsiFile(final VirtualFile virtualFile) {
    final GroovyFile[] myFindPsiFile = new GroovyFile[1];

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        myFindPsiFile[0] = (GroovyFile) PsiManager.getInstance(myProject).findFile(virtualFile);
      }
    });

    assert myFindPsiFile[0] != null;
    return myFindPsiFile[0];
  }

  //virtualFile -> PsiFile
  private List<String> generateItems(final VirtualFile item, final VirtualFile outputRootDirectory) {
    ProgressIndicator indicator = getProcessIndicator();
    if (indicator != null) indicator.setText(item.getPath());

    final GroovyFile file = findPsiFile(item);

    List<String> generatedJavaFilesRelPaths = ApplicationManager.getApplication().runReadAction(new Computable<List<String>>() {
      public List<String> compute() {
        return generate(file, outputRootDirectory);
      }
    });

    assert generatedJavaFilesRelPaths != null;

    return generatedJavaFilesRelPaths;
  }

  protected ProgressIndicator getProcessIndicator() {
    return myContext.getProgressIndicator();
  }

  private List<String> generate(final GroovyFile file, VirtualFile outputRootDirectory) {
    List<String> generatedItemsRelativePaths = new ArrayList<String>();

    GrTopStatement[] statements = getTopStatementsInReadAction(file);

    GrPackageDefinition packageDefinition = null;
    if (statements.length > 0 && statements[0] instanceof GrPackageDefinition) {
      packageDefinition = (GrPackageDefinition) statements[0];
    }

    if (file.isScript()) {
      VirtualFile virtualFile = file.getVirtualFile();
      assert virtualFile != null;
      String fileDefinitionName = virtualFile.getNameWithoutExtension();

      String topLevelGeneratedItemPath = createJavaSourceFile(outputRootDirectory, file, fileDefinitionName, file.getScriptClass(), packageDefinition);
      generatedItemsRelativePaths.add(topLevelGeneratedItemPath);
    }

    for (final GrTypeDefinition typeDefinition : file.getTypeDefinitions()) {
      String generatedItemPath = createJavaSourceFile(outputRootDirectory, file, typeDefinition.getName(), typeDefinition, packageDefinition);
      generatedItemsRelativePaths.add(generatedItemPath);
    }

    return generatedItemsRelativePaths;
  }

  private String getJavaClassPackage(GrPackageDefinition packageDefinition) {
    if (packageDefinition == null) return "";

    String prefix = packageDefinition.getPackageName();
    prefix = prefix.replace(".", PREFIX_SEPARATOR);
    prefix += PREFIX_SEPARATOR;

    return prefix;
  }

  private String createJavaSourceFile(VirtualFile outputRootDirectory, GroovyFileBase file, String typeDefinitionName, PsiClass typeDefinition, GrPackageDefinition packageDefinition) {
    //prefix defines structure of directories tree
    String prefix = "";
    if (packageDefinition != null) {
      prefix = getJavaClassPackage(packageDefinition);
    }

    StringBuffer text = new StringBuffer();

    writeTypeDefinition(text, typeDefinitionName, typeDefinition, packageDefinition);

    VirtualFile virtualFile = file.getVirtualFile();
    assert virtualFile != null;
//    String generatedFileRelativePath = prefix + typeDefinitionName + "." + "java";
    String fileShortName = typeDefinitionName + "." + "java";
    createGeneratedFile(text, outputRootDirectory.getPath(), prefix, fileShortName);
    return prefix + typeDefinitionName + "." + "java";
  }

  private GrTopStatement[] getTopStatementsInReadAction(final GroovyFileBase myPsiFile) {
    if (myPsiFile == null) return new GrTopStatement[0];

    return ApplicationManager.getApplication().runReadAction(new Computable<GrTopStatement[]>() {
      public GrTopStatement[] compute() {
        return myPsiFile.getTopStatements();
      }
    });
  }

  private boolean needsCreateClassFromFileName(GrTopStatement[] statements) {
    boolean isOnlyInnerTypeDef = true;
    for (GrTopStatement statement : statements) {
      if (!(statement instanceof GrTypeDefinition || statement instanceof GrImportStatement || statement instanceof GrPackageDefinition)) {
        isOnlyInnerTypeDef = false;
        break;
      }
    }
    return isOnlyInnerTypeDef;
  }

  private void writeTypeDefinition(StringBuffer text, String typeDefinitionName, PsiClass typeDefinition, GrPackageDefinition packageDefinition) {
    final boolean isScript = typeDefinition instanceof GroovyScriptClass;

    writePackageStatement(text, packageDefinition);

    GrMembersDeclaration[] membersDeclarations = typeDefinition instanceof GrTypeDefinition ? ((GrTypeDefinition) typeDefinition).getMemberDeclarations() : GrMembersDeclaration.EMPTY_ARRAY; //todo

    boolean isClassDef = typeDefinition instanceof GrClassDefinition;
    boolean isInterface = typeDefinition instanceof GrInterfaceDefinition;
    boolean isEnum = typeDefinition instanceof GrEnumTypeDefinition;
    boolean isAtInterface = typeDefinition instanceof GrAnnotationTypeDefinition;


    if (typeDefinition != null) {
      PsiModifierList modifierList = typeDefinition.getModifierList();

      boolean wasAddedModifiers = modifierList != null && writeTypeDefinitionMethodModifiers(text, modifierList, JAVA_TYPE_DEFINITION_MODIFIERS, typeDefinition.isInterface());
      if (!wasAddedModifiers) {
        text.append("public ");
      }
    }

    if (isInterface) text.append("interface");
    else if (isEnum) text.append("enum");
    else if (isAtInterface) text.append("@interface");
    else text.append("class");

    text.append(" ");

    text.append(typeDefinitionName);

    if (typeDefinition != null) {
      appendTypeParameters(text, typeDefinition);
    }

    text.append(" ");

    if (isScript) {
      text.append("extends ");
      text.append("groovy.lang.Script ");
    } else if (!isEnum && !isAtInterface) {
      final PsiClassType[] extendsClassesTypes = typeDefinition.getExtendsListTypes();

      if (extendsClassesTypes.length > 0) {
        text.append("extends ");
        text.append(computeTypeText(extendsClassesTypes[0]));
        text.append(" ");
      } else {
        if (isClassDef) {
          text.append("extends ");
          text.append(GrTypeDefinition.DEFAULT_BASE_CLASS_NAME);
          text.append(" ");
        }
      }

      PsiClassType[] implementsTypes = typeDefinition.getImplementsListTypes();

      if (implementsTypes.length > 0) {
        text.append(isInterface ? "extends " : "implements ");
        int i = 0;
        while (i < implementsTypes.length) {
          if (i > 0) text.append(", ");
          text.append(computeTypeText(implementsTypes[i]));
          text.append(" ");
          i++;
        }
      }
    }

    text.append("{");

    if (isEnum) {
      writeEnumConstants(text, (GrEnumTypeDefinition) typeDefinition);
    }

    Set<MethodSignature> methodSignatures = new HashSet<MethodSignature>();

    PsiMethod[] methods = typeDefinition == null ? PsiMethod.EMPTY_ARRAY : typeDefinition.getMethods();
    for (PsiMethod method : methods) {
      if (method instanceof GrConstructor) {
        writeConstructor(text, (GrConstructor) method, isEnum);
        continue;
      }

      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length > 0) {
        PsiParameter[] parametersCopy = new PsiParameter[parameters.length];
        PsiType[] parameterTypes = new PsiType[parameters.length];
        for (int i = 0; i < parameterTypes.length; i++) {
          parametersCopy[i] = parameters[i];
          parameterTypes[i] = parameters[i].getType();
        }

        for (int i = parameters.length - 1; i >= 0; i--) {
          MethodSignature signature = MethodSignatureUtil.createMethodSignature(method.getName(), parameterTypes, method.getTypeParameters(), PsiSubstitutor.EMPTY);
          if (methodSignatures.add(signature)) {
            writeMethod(text, method, parametersCopy);
          }

          PsiParameter parameter = parameters[i];
          if (!(parameter instanceof GrParameter) || !((GrParameter) parameter).isOptional()) break;
          parameterTypes = ArrayUtil.remove(parameterTypes, parameterTypes.length - 1);
          parametersCopy = ArrayUtil.remove(parametersCopy, parametersCopy.length - 1);
        }
      } else {
        MethodSignature signature = method.getSignature(PsiSubstitutor.EMPTY);
        if (methodSignatures.add(signature)) {
          writeMethod(text, method, parameters);
        }
      }
    }

    for (GrMembersDeclaration declaration : membersDeclarations) {
      if (declaration instanceof GrVariableDeclaration) {
        writeVariableDeclarations(text, (GrVariableDeclaration) declaration);
      }
    }

    text.append("}");
  }

  private void appendTypeParameters(StringBuffer text, PsiTypeParameterListOwner typeParameterListOwner) {
    if (typeParameterListOwner.hasTypeParameters()) {
      text.append("<");
      PsiTypeParameter[] parameters = typeParameterListOwner.getTypeParameters();
      for (int i = 0; i < parameters.length; i++) {
        if (i > 0) text.append(", ");
        PsiTypeParameter parameter = parameters[i];
        text.append(parameter.getName());
        PsiClassType[] extendsListTypes = parameter.getExtendsListTypes();
        if (extendsListTypes.length > 0) {
          text.append( " extends ");
          for (int j = 0; j < extendsListTypes.length; j++) {
            if (j > 0) text.append(" & ");
            text.append(computeTypeText(extendsListTypes[j]));
          }
        }
      }
      text.append(">");
    }
  }

  private void writeEnumConstants(StringBuffer text, GrEnumTypeDefinition enumDefinition) {
    text.append("\n  ");
    GrEnumConstant[] enumConstants = enumDefinition.getEnumConstants();
    for (int i = 0; i < enumConstants.length; i++) {
      if (i > 0) text.append(", ");
      GrEnumConstant enumConstant = enumConstants[i];
      text.append(enumConstant.getName());
      PsiMethod constructor = enumConstant.resolveConstructor();
      if (constructor != null) {
        text.append("(");
        writeStubConstructorInvocation(text, constructor);
        text.append(")");
      }

      GrTypeDefinitionBody block = enumConstant.getAnonymousBlock();
      if (block != null) {
        text.append("{\n");
        PsiMethod[] methods = block.getMethods();
        for (PsiMethod method : methods) {
          writeMethod(text, method, method.getParameterList().getParameters());
        }
        text.append("}");
      }
    }
    text.append(";");
  }

  private void writeStubConstructorInvocation(StringBuffer text, PsiMethod constructor) {
    final PsiParameter[] superParams = constructor.getParameterList().getParameters();
    for (int j = 0; j < superParams.length; j++) {
      if (j > 0) text.append(", ");
      String typeText = getTypeText(superParams[j].getType());
      text.append("(").append(typeText).append(")").append(getDefaultValueText(typeText));
    }
  }

  private void writePackageStatement(StringBuffer text, GrPackageDefinition packageDefinition) {
    if (packageDefinition != null) {
      text.append("package ");
      text.append(packageDefinition.getPackageName());
      text.append(";");
      text.append("\n");
      text.append("\n");
    }
  }

  private void writeConstructor(final StringBuffer text, GrConstructor constructor, boolean isEnum) {
    text.append("\n");
    text.append("  ");
    if (!isEnum) {
      writeMethodModifiers(text, constructor.getModifierListGroovy(), JAVA_MODIFIERS);
    }

    /************* name **********/
    //append constructor name
    text.append(constructor.getName());

    /************* parameters **********/
    GrParameter[] parameterList = constructor.getParameters();

    text.append("(");
    String paramType;
    GrTypeElement paramTypeElement;

    for (int i = 0; i < parameterList.length; i++) {
      if (i > 0) text.append(", ");

      GrParameter parameter = parameterList[i];
      paramTypeElement = parameter.getTypeElementGroovy();
      paramType = getTypeText(paramTypeElement);

      text.append(paramType);
      text.append(" ");
      text.append(parameter.getName());
    }

    text.append(")");
    text.append(" ");

    /************* body **********/

    final GrConstructorInvocation constructorInvocation = constructor.getChainingConstructorInvocation();
    if (constructorInvocation != null) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          PsiMethod chainedConstructor = constructorInvocation.resolveConstructor();
          if (chainedConstructor == null) {
            final GroovyResolveResult[] results = constructorInvocation.multiResolveConstructor();
            if (results.length > 0) {
              chainedConstructor = (PsiMethod) results[0].getElement();
            }
          }

          if (chainedConstructor != null) {
            final PsiClassType[] throwsTypes = chainedConstructor.getThrowsList().getReferencedTypes();
            if (throwsTypes.length > 0) {
              text.append(" throws ");
              for (int i = 0; i < throwsTypes.length; i++) {
                if (i > 0) text.append(", ");
                text.append(getTypeText(throwsTypes[i]));
              }
            }
          }

          text.append("{\n");

          text.append("    ");
          if (constructorInvocation.isSuperCall()) {
            text.append("super");
          } else {
            text.append("this");
          }
          text.append("(");

          if (chainedConstructor != null) {
            writeStubConstructorInvocation(text, chainedConstructor);
          }

          text.append(")");
          text.append(";");
        }
      });

    } else {
      text.append("{\n");
    }

    text.append("\n  }");
    text.append("\n");
  }

  private String getDefaultValueText(String typeCanonicalText) {
    final String result = typesToInitialValues.get(typeCanonicalText);
    if (result == null) return "null";
    return result;
  }

  private void writeVariableDeclarations(StringBuffer text, GrVariableDeclaration variableDeclaration) {
    GrTypeElement varTypeElement = variableDeclaration.getTypeElementGroovy();
    String varQualifiedTypeName = getTypeText(varTypeElement);

    String initValueText = getDefaultValueText(varQualifiedTypeName);

    //append method name
    PsiModifierList modifierList = variableDeclaration.getModifierListGroovy();
    GrVariable[] variables = variableDeclaration.getVariables();
    GrVariable variable;
    int i = 0;
    while (i < variables.length) {
      variable = variables[i];
      String name = variable.getName();
      if (!variable.getManager().getNameHelper().isIdentifier(name)) continue; //does not have a java image

      text.append("\n");
      text.append("  ");
      writeVariableDefinitionModifiers(text, modifierList, JAVA_MODIFIERS);

      //type
      text.append(varQualifiedTypeName);
      text.append(" ");

      //var name
      text.append(name);
      text.append(" = ");

      text.append(initValueText);
      text.append(";");
      text.append("\n");
      i++;
    }
  }

  private void writeMethod(StringBuffer text, PsiMethod method, final PsiParameter[] parameters) {
    if (method == null) return;
    String name = method.getName();
    if (!method.getManager().getNameHelper().isIdentifier(name)) return; //does not have a java image

    boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);

    PsiModifierList modifierList = method.getModifierList();

    text.append("\n");
    text.append("  ");
    writeMethodModifiers(text, modifierList, JAVA_MODIFIERS);
    if (method.hasTypeParameters()) {
      appendTypeParameters(text, method);
      text.append(" ");
    }

    //append return type
    PsiType retType;
    if (method instanceof GrMethod) {
      retType = ((GrMethod) method).getDeclaredReturnType();
      if (retType == null) retType = TypesUtil.getJavaLangObject((GrMethod)method);
    } else retType = method.getReturnType();

    text.append(getTypeText(retType));
    text.append(" ");

    //append method name
    text.append(name);

    /************* parameters **********/

    text.append("(");

    //writes myParameters
    int i = 0;
    while (i < parameters.length) {
      PsiParameter parameter = parameters[i];
      if (parameter == null) continue;

      if (i > 0) text.append(", ");  //append ','

      text.append(getTypeText(parameter.getType()));
      text.append(" ");
      text.append(parameter.getName());

      i++;
    }
    text.append(")");
    text.append(" ");

    if (!isAbstract) {
      /************* body **********/
      text.append("{\n");
      text.append("    return ");

      text.append(getDefaultValueText(getTypeText(retType)));

      text.append(";");

      text.append("\n  }");
    } else {
      text.append(";");
    }
    text.append("\n");
  }

  private boolean writeMethodModifiers(StringBuffer text, PsiModifierList modifierList, String[] modifiers) {
    boolean wasAddedModifiers = false;
    for (String modifierType : modifiers) {
      if (modifierList.hasModifierProperty(modifierType)) {
        text.append(modifierType);
        text.append(" ");
        wasAddedModifiers = true;
      }
    }
    return wasAddedModifiers;
  }

  private boolean writeVariableDefinitionModifiers(StringBuffer text, PsiModifierList modifierList, String[] modifiers) {
    boolean wasAddedModifiers = false;
    for (String modifierType : modifiers) {
      if (modifierList.hasModifierProperty(modifierType)) {
        text.append(modifierType);
        text.append(" ");
        wasAddedModifiers = true;
      }
    }
    return wasAddedModifiers;
  }

  private boolean writeTypeDefinitionMethodModifiers(StringBuffer text, PsiModifierList modifierList, String[] modifiers, boolean isInterface) {
    boolean wasAddedModifiers = false;
    for (String modifierType : modifiers) {
      if (modifierList.hasModifierProperty(modifierType)) {
        if (PsiModifier.ABSTRACT.equals(modifierType) && isInterface) {
          continue;
        }
        text.append(modifierType);
        text.append(" ");
        wasAddedModifiers = true;
      }
    }
    return wasAddedModifiers;
  }

  private String getTypeText(GrTypeElement typeElement) {
    if (typeElement == null) {
      return "java.lang.Object";
    } else {
      return computeTypeText(typeElement.getType());
    }
  }

  private String getTypeText(PsiType type) {
    if (type == null) {
      return "java.lang.Object";
    } else {
      return computeTypeText(type);
    }
  }

  private String computeTypeText(PsiType type) {
    if (type instanceof PsiArrayType) {
      String componentText = computeTypeText(((PsiArrayType) type).getComponentType());
      if (type instanceof PsiEllipsisType) return componentText + "...";
      return componentText + "[]";
    }

    String canonicalText = type.getCanonicalText();
    return canonicalText != null ? canonicalText : type.getPresentableText();
  }

  private void createGeneratedFile(StringBuffer text, String outputDir, String prefix, String generatedItemPath) {
    assert prefix != null;

    String prefixWithoutSeparator = prefix;

    if (!"".equals(prefix)) {
      prefixWithoutSeparator = prefix.substring(0, prefix.length() - PREFIX_SEPARATOR.length());
      new File(outputDir, prefixWithoutSeparator).mkdirs();
    }

    File myFile;
    if (!"".equals(prefix))
      myFile = new File(outputDir + File.separator + prefixWithoutSeparator, generatedItemPath);
    else
      myFile = new File(outputDir, generatedItemPath);

    BufferedWriter writer = null;
    try {
      Writer fileWriter = new FileWriter(myFile);
      writer = new BufferedWriter(fileWriter);
      writer.write(text.toString());
    } catch (IOException e) {
      LOG.error(e);
    } finally {
      try {
        assert writer != null;
        writer.close();
      } catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  @NotNull
  public String getDescription() {
    return "Groovy to java source code generator";
  }

  public boolean validateConfiguration(CompileScope scope) {
//    scope.getFiles(GroovyFileType.GROOVY_FILE_TYPE, true);
    return true;
  }

  public ValidityState createValidityState(DataInputStream is) throws IOException {
    return TimestampValidityState.load(is);
  }

  CharTrie myTrie = new CharTrie();

  public void compilationFinished(boolean aborted, int errors, int warnings, final CompileContext compileContext) {
    myTrie.clear();
  }

  class GenerationItemImpl implements GenerationItem {
    ValidityState myState;
    private boolean myInTestSources;
    final Module myModule;
    public int myHashCode;
    private VirtualFile myVFile;

    public GenerationItemImpl(String path, Module module, ValidityState state, boolean isInTestSources, VirtualFile vFile) {
      myVFile = vFile;
      myModule = module;
      myState = state;
      myInTestSources = isInTestSources;
      myHashCode = myTrie.getHashCode(path);
    }

    public String getPath() {
      return myTrie.getString(myHashCode);
    }

    public ValidityState getValidityState() {
      return myState;
    }

    public Module getModule() {
      return myModule;
    }

    public boolean isTestSource() {
      return myInTestSources;
    }

    public VirtualFile getVFile() {
      return myVFile;
    }
  }
}