package org.jetbrains.plugins.groovy.compiler.generator;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrInterfaceDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrModifierListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrConstructorDefinitionImpl;
import org.jetbrains.plugins.groovy.util.containers.CharTrie;

import java.io.*;
import java.util.*;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 03.05.2007
 */
public class GroovyToJavaGenerator implements SourceGeneratingCompiler, CompilationStatusListener {
  private static final Map<String, String> typesToInitialValues = new HashMap<String, String>();

  static {
    typesToInitialValues.put("String", "\"\"");
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
      final GroovyFile psiFile = findPsiFile(file);
      boolean isInTestSources = ProjectRootManager.getInstance(myProject).getFileIndex().isInTestSourceContent(file);

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
    return context.getCompileScope().getFiles(GroovyFileType.GROOVY_FILE_TYPE, true);
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
    assert myContext != null;
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

      String topLevelGeneratedItemPath = createJavaSourceFile(outputRootDirectory, file, fileDefinitionName, null, packageDefinition);
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

  private String createJavaSourceFile(VirtualFile outputRootDirectory, GroovyFile file, String typeDefinitionName, GrTypeDefinition typeDefinition, GrPackageDefinition packageDefinition) {
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

  private GrTopStatement[] getTopStatementsInReadAction(final GroovyFile myPsiFile) {
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

  private void writeTypeDefinition(StringBuffer text, String typeDefinitionName, GrTypeDefinition typeDefinition, GrPackageDefinition packageDefinition) {
    boolean isScript = typeDefinition == null;

    writePackageStatement(text, packageDefinition);

    GrStatement[] statements = typeDefinition == null ? GrStatement.EMPTY_ARRAY : typeDefinition.getStatements();

    boolean isClassDef = typeDefinition instanceof GrClassDefinition;
    boolean isInterface = typeDefinition instanceof GrInterfaceDefinition;


    if (typeDefinition != null) {
      PsiModifierList modifierList = typeDefinition.getModifierList();

      boolean wasAddedModifiers = writeTypeDefinitionMethodModifiers(text, modifierList, JAVA_TYPE_DEFINITION_MODIFIERS);
      if (!wasAddedModifiers) {
        text.append("public ");
      }
    }

    if (isScript) {
      text.append("public ");
    }

//    text.append(" ");

    if (isInterface) text.append("interface");
    else text.append("class");

    text.append(" ");

    text.append(typeDefinitionName);
    text.append(" ");

    if (isScript) {
      text.append("extends ");
      text.append("groovy.lang.Script ");
    } else {
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
        text.append("implements ");
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

    boolean wasRunMethodPresent = false;

    Map<String, String> gettersNames = new HashMap<String, String>();
    Map<String, String> settersNames = new HashMap<String, String>();

    for (GrStatement statement : statements) {
      if (statement instanceof GrMethod) {
        final GrMethod method = (GrMethod) statement;
        if (method.isConstructor()) {
          writeConstructor(text, method);
        }

        writeMethod(text, method);

        getDefinedGetters(gettersNames, method);
        getDefinedSetters(settersNames, method);

        wasRunMethodPresent = wasRunMethod(method);
      }
      if (statement instanceof GrVariableDeclaration) {
        writeVariableDeclarations(text, (GrVariableDeclaration) statement);
      }
    }

    for (GrStatement statement : statements) {
      if (statement instanceof GrVariableDeclaration) {
        writeGetterAndSetter(text, (GrVariableDeclaration) statement, gettersNames, settersNames);
      }
    }

    if (isScript && !wasRunMethodPresent) {
      writeRunMethod(text);
    }

    text.append("}");
  }

  private Map<String, String> getDefinedGetters(Map<String, String> gettersNames, GrMethod method) {
    String getVariable;
    String methodName = method.getNameIdentifierGroovy().getText();
    if (methodName.startsWith("get")) {
      String var = methodName.substring(methodName.indexOf("get") + "get".length());

      if (var.length() != 0) {
        getVariable = Character.toLowerCase(var.charAt(0)) + var.substring(1);
        GrTypeElement type = method.getReturnTypeElementGroovy();
        if (type != null) {
          gettersNames.put(getVariable, computeTypeText(type.getType()));
        } else {
          gettersNames.put(getVariable, "java.lang.Object");
        }
      }
    }
    return gettersNames;
  }

  private Map<String, String> getDefinedSetters(Map<String, String> settersNames, GrMethod method) {
    String setVariable;
    String methodName = method.getNameIdentifierGroovy().getText();
    if (methodName.startsWith("set")) {
      String var = methodName.substring(methodName.indexOf("set") + "set".length());

      if (var.length() != 0) {
        setVariable = Character.toLowerCase(var.charAt(0)) + var.substring(1);
        GrParameter[] parameters = method.getParameters();

        if (parameters.length == 1) {
          GrParameter parameter = parameters[0];
          GrTypeElement type = parameter.getTypeElementGroovy();

          if (type != null) {
            settersNames.put(setVariable, computeTypeText(type.getType()));
          } else {
            settersNames.put(setVariable, "java.lang.Object");
          }
        }
      }
    }
    return settersNames;
  }

  private boolean wasRunMethod(GrMethod method) {
    boolean runMethodPresent = false;
    if ("run".equals(method.getName())) {
      PsiType returnType = method.getReturnType();

      runMethodPresent = returnType != null && "java.lang.Object".equals(computeTypeText(returnType));
    }
    return runMethodPresent;
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

  private void writeGetterAndSetter(final StringBuffer text, final GrVariableDeclaration variableDeclaration, final Map<String, String> gettersNames, final Map<String, String> settersNames) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        writeGetter(text, variableDeclaration, gettersNames);
        writeSetter(text, variableDeclaration, settersNames);
      }
    });
  }

  private void writeGetter(StringBuffer text, GrVariableDeclaration variableDeclaration, Map<String, String> gettersNames) {
    GrModifierListImpl list = (GrModifierListImpl) variableDeclaration.getModifierList();

    GrTypeElement element = variableDeclaration.getTypeElementGroovy();
    String type;
    if (element == null) {
      type = "java.lang.Object";
    } else {
      type = computeTypeText(element.getType());
    }

    for (GrVariable variable : variableDeclaration.getVariables()) {
      String name = variable.getName();

      if (name == null) continue;

      if (gettersNames.containsKey(name)) {
        continue;
      }

      text.append("\n");
      text.append("  ");
      writeMethodModifiers(text, list, JAVA_MODIFIERS);
      text.append(type);
      text.append(" ");
      text.append("get").append(StringUtil.capitalize(name));

      text.append("()");
      text.append(" ");
      text.append("{\n");

      String returnValue = typesToInitialValues.get(type);

      if (returnValue == null) returnValue = "null";

      text.append("    return ");
      text.append(returnValue);
      text.append(";");
      text.append("\n  }");
      text.append("\n");
    }

//    if (wasGetter) text.append("\n");
  }

  private void writeSetter(StringBuffer text, GrVariableDeclaration variableDeclaration, Map<String, String> settersNames) {
    GrModifierListImpl modifierList = (GrModifierListImpl) variableDeclaration.getModifierList();
    if (modifierList.hasVariableModifierProperty(PsiModifier.FINAL)) return;

    GrTypeElement element = variableDeclaration.getTypeElementGroovy();
    String type;
    if (element == null) {
      type = "java.lang.Object";
    } else {
      type = computeTypeText(element.getType());
    }

    for (GrVariable variable : variableDeclaration.getVariables()) {
      String name = variable.getName();

      if (name == null) continue;

      if (settersNames.containsKey(name)) {
        String setterType = settersNames.get(name);
        if (setterType.equals(type)) {
          continue;
        }
      }

      text.append("\n");
      text.append("  ");
      writeMethodModifiers(text, modifierList, JAVA_MODIFIERS);

      text.append("void ");
      text.append("set").append(StringUtil.capitalize(name));

      text.append("(");
      text.append(type);
      text.append(" ");
      text.append("new").append(StringUtil.capitalize(name));
      text.append(")");
      text.append(" ");
      text.append("{\n");
      text.append("    return;");
      text.append("\n  }");
      text.append("\n");
    }

//    if (wasSetter) text.append("\n");
  }

  private void writeRunMethod(StringBuffer text) {
    text.append("\n  public java.lang.Object run() {\n" +
        "    return null;\n" +
        "  }\n");
  }

  private void writeConstructor(StringBuffer text, GrMethod constructor) {
    GrConstructorDefinitionImpl constrDefinition = (GrConstructorDefinitionImpl) constructor;

    writeMethodModifiers(text, constrDefinition.getModifierList(), JAVA_MODIFIERS);

    /************* name **********/
    text.append("\n");
    //append constructor name
    text.append(constructor.getName());

    /************* parameters **********/
    GrParameter[] parameterList = constructor.getParameters();

    text.append("(");
    String paramType;
    GrTypeElement paramTypeElement;

    //writes parameters
    int i = 0;
    while (i < parameterList.length) {
      if (i > 0) text.append(", ");  //append ','

      GrParameter parameter = parameterList[i];
      paramTypeElement = parameter.getTypeElementGroovy();
      paramType = getTypeText(paramTypeElement);

      text.append(paramType);
      text.append(" ");
      text.append(parameter.getName());

      i++;
    }
    text.append(")");
    text.append(" ");

    /************* body **********/
    text.append("{\n");
    PsiParameterList list = constructor.getParameterList();
    PsiParameter[] parameters = list.getParameters();

    GrConstructorInvocation grConstructorInvocation = constrDefinition.getConstructorInvocation();
    if (grConstructorInvocation != null && grConstructorInvocation.isSuperCall()) {
      text.append("  ");
      text.append("super");
      text.append("(");

      int i1 = 0;
      while (i1 < parameters.length) {

        if (i1 > 0) text.append(", ");

        PsiParameter grParameter = parameters[i1];
        PsiType type = grParameter.getType();
        String initValueToText;

        String typeText = computeTypeText(type);
        if (typesToInitialValues.containsKey(typeText))
          initValueToText = typesToInitialValues.get(typeText);
        else
          initValueToText = "null";

        text.append(initValueToText);
        i1++;
      }
      text.append(")");
      text.append(";");
    }
    text.append("\n}");
    text.append("\n");
  }

  private void writeVariableDeclarations(StringBuffer text, GrVariableDeclaration variableDeclaration) {
    GrTypeElement varTypeElement = variableDeclaration.getTypeElementGroovy();
    String varQualifiedTypeName = getTypeText(varTypeElement);

    String initValueToText;
    if (typesToInitialValues.containsKey(varQualifiedTypeName))
      initValueToText = typesToInitialValues.get(varQualifiedTypeName);
    else
      initValueToText = "null";

    //append method name
    PsiModifierList modifierList = variableDeclaration.getModifierList();
    GrVariable[] grVariables = variableDeclaration.getVariables();
    GrVariable variable;
    int i = 0;
    while (i < grVariables.length) {
      variable = grVariables[i];

      text.append("\n");
      text.append("  ");
      writeVariableDefinitionModifiers(text, modifierList, JAVA_MODIFIERS);

      //type
      text.append(varQualifiedTypeName);
      text.append(" ");

      //var name
      text.append(variable.getName());
      text.append(" = ");

      text.append(initValueToText);
      text.append(";");
      text.append("\n");
      i++;
    }
  }

  private void writeMethod(StringBuffer text, GrMethod method) {
    boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);

    /************* type and name **********/
    GrTypeElement typeElement = method.getReturnTypeElementGroovy();
    String qualifiedTypeName = getTypeText(typeElement);

    PsiModifierList modifierList = method.getModifierList();

    text.append("\n");
    text.append("  ");
    writeMethodModifiers(text, modifierList, JAVA_MODIFIERS);

    //append qualified type name
    text.append(qualifiedTypeName);
    text.append(" ");

    //append method name
    text.append(method.getName());

    /************* parameters **********/
    GrParameter[] parameterList = method.getParameters();

    text.append("(");
    String paramType;
    GrTypeElement paramTypeElement;

    //writes parameters
    int i = 0;
    while (i < parameterList.length) {
      if (i > 0) text.append(", ");  //append ','

      GrParameter parameter = parameterList[i];
      paramTypeElement = parameter.getTypeElementGroovy();
      paramType = getTypeText(paramTypeElement);

      text.append(paramType);
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

      if (typesToInitialValues.containsKey(qualifiedTypeName))
        text.append(typesToInitialValues.get(qualifiedTypeName));
      else
        text.append("null");

      text.append(";");

      text.append("\n  }");
    } else {
      text.append(";");
    }
    text.append("\n");
  }

  private boolean writeMethodModifiers(StringBuffer text, PsiModifierList modifierList, String[] modifiers) {
    assert modifierList instanceof GrModifierListImpl;
    GrModifierListImpl list = (GrModifierListImpl) modifierList;

    boolean wasAddedModifiers = false;
    for (String modifierType : modifiers) {
      if (list.hasMethodModifierProperty(modifierType)) {
        text.append(modifierType);
        text.append(" ");
        wasAddedModifiers = true;
      }
    }
    return wasAddedModifiers;
  }

  private boolean writeVariableDefinitionModifiers(StringBuffer text, PsiModifierList modifierList, String[] modifiers) {
    assert modifierList instanceof GrModifierListImpl;
    GrModifierListImpl list = (GrModifierListImpl) modifierList;

    boolean wasAddedModifiers = false;
    for (String modifierType : modifiers) {
      if (list.hasVariableModifierProperty(modifierType)) {
        text.append(modifierType);
        text.append(" ");
        wasAddedModifiers = true;
      }
    }
    return wasAddedModifiers;
  }

  private boolean writeTypeDefinitionMethodModifiers(StringBuffer text, PsiModifierList modifierList, String[] modifiers) {
    assert modifierList instanceof GrModifierListImpl;
    GrModifierListImpl list = (GrModifierListImpl) modifierList;

    boolean wasAddedModifiers = false;
    for (String modifierType : modifiers) {
      if (list.hasClassExplicitModifier(modifierType)) {
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

  private String computeTypeText(PsiType type) {
    if (type instanceof PsiArrayType) {
      return computeTypeText(((PsiArrayType) type).getComponentType()) + "[]";
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
      e.printStackTrace();
    } finally {
      try {
        assert writer != null;
        writer.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
//      System.out.println("");
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