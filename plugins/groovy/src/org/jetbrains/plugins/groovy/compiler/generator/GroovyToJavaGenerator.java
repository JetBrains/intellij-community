package org.jetbrains.plugins.groovy.compiler.generator;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

  public GenerationItem[] getGenerationItems(CompileContext context) {
    myContext = context;

    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            VirtualFileManager.getInstance().refresh(false);
          }
        });
      }
    }, ModalityState.NON_MODAL);

    VirtualFile[] files = context.getCompileScope().getFiles(GroovyFileType.GROOVY_FILE_TYPE, true);

    List<GenerationItem> generationItems = new ArrayList<GenerationItem>();
    GenerationItem item;
    for (VirtualFile file : files) {
      final GroovyFile myPsiFile = findPsiFile(file);

      GrTopStatement[] statements = getTopStatementsInReadAction(myPsiFile);

      boolean needCreateTopLevelClass = !needsCreateClassFromFileName(statements);

      String prefix = "";
      if (statements.length > 0 && statements[0] instanceof GrPackageDefinition) {
        prefix = getJavaClassPackage((GrPackageDefinition) statements[0]);
      }

      //top level class
      VirtualFile virtualFile;
      if (needCreateTopLevelClass) {

        virtualFile = myPsiFile.getVirtualFile();
        assert virtualFile != null;
        generationItems.add(new GenerationItemImpl(prefix + virtualFile.getNameWithoutExtension() + "." + "java", context.getModuleByFile(virtualFile), new TimestampValidityState(file.getTimeStamp())));
      }

      GrTypeDefinition[] typeDefinitions = ApplicationManager.getApplication().runReadAction(new Computable<GrTypeDefinition[]>() {
        public GrTypeDefinition[] compute() {
          return myPsiFile.getTypeDefinitions();
        }
      });

      for (GrTypeDefinition typeDefinition : typeDefinitions) {
        item = new GenerationItemImpl(prefix + typeDefinition.getNameIdentifierGroovy().getText() + "." + "java", context.getModuleByFile(file), new TimestampValidityState(file.getTimeStamp()));
        generationItems.add(item);
      }
    }
    return generationItems.toArray(new GenerationItem[generationItems.size()]);
  }

  public GenerationItem[] generate(CompileContext context, GenerationItem[] itemsToGenerate, VirtualFile outputRootDirectory) {
    VirtualFile[] files = context.getCompileScope().getFiles(GroovyFileType.GROOVY_FILE_TYPE, true);

    List<GenerationItem> generatedItems = new ArrayList<GenerationItem>();
    Map<String, GenerationItem> myPathsToItemsMap = new HashMap<String, GenerationItem>();

    //puts items witch can be generated
    for (GenerationItem item : itemsToGenerate) {
      myPathsToItemsMap.put(item.getPath(), item);
    }

    for (VirtualFile groovyFile : files) {
      //generate java classes form groovy source files
      VirtualFile itemFile = VirtualFileManager.getInstance().findFileByUrl(groovyFile.getUrl());
      assert itemFile != null;

      List<String> generatedJavaFilesRelPaths = generateItems(itemFile, outputRootDirectory);
      for (String relPath : generatedJavaFilesRelPaths) {
        GenerationItem generationItem = myPathsToItemsMap.get(relPath);
        if (generationItem != null)
          generatedItems.add(generationItem);
      }
    }

    return generatedItems.toArray(new GenerationItem[0]);
  }

  private GroovyFile findPsiFile(final VirtualFile virtualFile) {
    final Project project = VfsUtil.guessProjectForFile(virtualFile);
    assert project != null;
    final GroovyFile[] myFindPsiFile = new GroovyFile[1];

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        myFindPsiFile[0] = (GroovyFile) PsiManager.getInstance(project).findFile(virtualFile);
      }
    });

    assert myFindPsiFile[0] != null;
    return myFindPsiFile[0];
  }

  //virtualFile -> PsiFile
  private List<String> generateItems(final VirtualFile item, final VirtualFile outputRootDirectory) {
    assert myContext != null;
    myContext.getProgressIndicator().setText(item.getPath());

    GroovyFile myPsiFile = findPsiFile(item);

    List<String> generatedJavaFilesRelPaths = generate(myPsiFile, outputRootDirectory);
    assert generatedJavaFilesRelPaths != null;

    return generatedJavaFilesRelPaths;
  }

  private List<String> generate(final GroovyFile myPsiFile, VirtualFile outputRootDirectory) {
    List<String> generatedItemsRelativePaths = new ArrayList<String>();

    final StringBuffer text = new StringBuffer();
    GrTopStatement[] statements = getTopStatementsInReadAction(myPsiFile);

    //there is member on top level
    boolean isOnlyInnerTypeDef = needsCreateClassFromFileName(statements);

    GrPackageDefinition packageDefinition = null;
    if (statements.length > 0 && statements[0] instanceof GrPackageDefinition) {
      packageDefinition = (GrPackageDefinition) statements[0];
    }

    if (statements.length != 0 && !isOnlyInnerTypeDef) {
      VirtualFile virtualFile = myPsiFile.getVirtualFile();
      assert virtualFile != null;
      String fileDefinitionName = virtualFile.getNameWithoutExtension();

      String topLevelGeneratedItemPath = createJavaSourceFile(outputRootDirectory, myPsiFile, text, fileDefinitionName, null, packageDefinition);
      generatedItemsRelativePaths.add(topLevelGeneratedItemPath);
    }

    GrTypeDefinition[] typeDefinitions = ApplicationManager.getApplication().runReadAction(new Computable<GrTypeDefinition[]>() {
      public GrTypeDefinition[] compute() {
        return myPsiFile.getTypeDefinitions();
      }
    });

    String generatedItemPath;
    for (final GrTypeDefinition typeDefinition : typeDefinitions) {
      text.setLength(0);
      PsiElement element = typeDefinition.getNameIdentifierGroovy();

      generatedItemPath = createJavaSourceFile(outputRootDirectory, myPsiFile, text, element.getText(), typeDefinition, packageDefinition);
      generatedItemsRelativePaths.add(generatedItemPath);
    }

    return generatedItemsRelativePaths;
  }

  /* @return prefix;
  * prefix = (path + File.separator) | ""
  */

  private String getJavaClassPackage(GrPackageDefinition packageDefinition) {
    if (packageDefinition == null) return "";

    String prefix = "";
    prefix = packageDefinition.getPackageName();
    prefix = prefix.replace(".", PREFIX_SEPARATOR);
    prefix += PREFIX_SEPARATOR;

    return prefix;
  }

  private String createJavaSourceFile(VirtualFile outputRootDirectory, GroovyFile myPsiFile, StringBuffer text, String typeDefinitionName, GrTypeDefinition typeDefinition, GrPackageDefinition packageDefinition) {
    //prefix defines structure of directories tree
    String prefix = "";
    if (packageDefinition != null) {
      prefix = getJavaClassPackage(packageDefinition);
    }

    writeTypeDefinition(text, typeDefinitionName, typeDefinition, packageDefinition);

    VirtualFile virtualFile = myPsiFile.getVirtualFile();
    assert virtualFile != null;
//    String generatedFileRelativePath = prefix + typeDefinitionName + "." + "java";
    String fileShortName = typeDefinitionName + "." + "java";
    createGeneratedFile(text, outputRootDirectory.getPath(), prefix, fileShortName, myPsiFile);
    return prefix + typeDefinitionName + "." + "java";
  }

  private GrStatement[] getStatementsInReadAction(final GrTypeDefinition typeDefinition) {
    if (typeDefinition == null) return new GrStatement[0];

    return ApplicationManager.getApplication().runReadAction(new Computable<GrStatement[]>() {
      public GrStatement[] compute() {
        return typeDefinition.getStatements();
      }
    });
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

    if (packageDefinition != null) {
      text.append("package ");
      text.append(packageDefinition.getPackageName());
      text.append(";");
      text.append("\n");
    }

    GrStatement[] statements = getStatementsInReadAction(typeDefinition);

    boolean isClassDef = typeDefinition instanceof GrClassDefinition;
    boolean isInteraface = typeDefinition instanceof GrInterfaceDefinition;


    if (typeDefinition != null) {
      PsiModifierList modifierList = typeDefinition.getModifierList();

      boolean wasAddedModifiers = writeTypeDefinitionMethodModifiers(text, modifierList, JAVA_TYPE_DEFINITION_MODIFIERS);
      if (!wasAddedModifiers) {
        text.append("public");
      }
    }

    text.append(" ");

    if (isInteraface) text.append("interface");
    else text.append("class");

    text.append(" ");

    text.append(typeDefinitionName);
    text.append(" ");

    if (isScript) {
      text.append("extends ");
      text.append("groovy.lang.Script");
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
        }
      }

      PsiClassType[] implementsTypes = typeDefinition.getImplementsListTypes();

      if (implementsTypes.length > 0) {
        text.append(" implements ");
        int i = 0;
        while (i < implementsTypes.length) {
          if (i > 0) text.append(", ");
          text.append(computeTypeText(implementsTypes[i]));
          text.append(" ");
          i++;
        }
        text.append(" ");
      }
    }

    text.append(" ");
    text.append("{");
    text.append("\n");

    boolean isRunMethodWrote = false;

    for (GrTopStatement statement : statements) {
      if (statement instanceof GrMethod) {
        if (((GrMethod) statement).isConstructor()) {
          writeConstructor(text, (GrMethod) statement);
          text.append("\n");
        }
        writeMethod(text, (GrMethod) statement, isInteraface);
        text.append("\n");

        isRunMethodWrote = "run".equals(((GrMethod) statement).getNameIdentifierGroovy().getText()) &&
            ((GrMethod) statement).getReturnTypeElementGroovy() != null &&
            "java.lang.Object".equals(computeTypeText(((GrMethod) statement).getReturnTypeElementGroovy().getType()));

      }
      if (statement instanceof GrVariableDeclaration) {
        writeVariableDeclarations(text, (GrVariableDeclaration) statement);
        writeGetterAndSetter(text, (GrVariableDeclaration) statement);
      }
    }

    if (isScript && !isRunMethodWrote) writeRunMethod(text);

    text.append("}");
  }

  private void writeGetterAndSetter(final StringBuffer text, final GrVariableDeclaration variableDeclaration) {
    ApplicationManager.getApplication().runReadAction(new Runnable () {
      public void run() {
        writeGetter(text, variableDeclaration);
        writeSetter(text, variableDeclaration);
      }
    });
  }

  private void writeGetter(StringBuffer text, GrVariableDeclaration variableDeclaration) {
    GrModifierListImpl list = (GrModifierListImpl) variableDeclaration.getModifierList();

    GrTypeElement element = variableDeclaration.getTypeElementGroovy();
    String type;
    if (element == null) {
      type = "java.lang.Object";
    } else {
      type = computeTypeText(element.getType());
    }


    for (GrVariable variable : variableDeclaration.getVariables()) {
      writeMethodModifiers(text, list, JAVA_MODIFIERS);
      text.append(type);
      text.append(" ");
      text.append("get").append(StringUtil.capitalize(variable.getNameIdentifierGroovy().getText()));

      text.append("()");
      text.append(" ");
      text.append("{");

      String returnType = typesToInitialValues.get(type);

      if (returnType == null) returnType = "null";


      text.append("return ");
      text.append(returnType);
      text.append(";");
      text.append("}");
      text.append("\n");
    }
  }

  private void writeSetter(StringBuffer text, GrVariableDeclaration variableDeclaration) {
    GrModifierListImpl modifierList = (GrModifierListImpl) variableDeclaration.getModifierList();
    if (modifierList.hasVariableModifierProperty(PsiModifier.FINAL)) return;

    GrTypeElement element = variableDeclaration.getTypeElementGroovy();
    String type;
    if (element == null) {
      type = "java.lang.Object";
    } else {
      type = computeTypeText(element.getType());
    }


    for (GrVariable grVariable : variableDeclaration.getVariables()) {
      writeMethodModifiers(text, modifierList, JAVA_MODIFIERS);

      text.append("void ");
      text.append("set").append(StringUtil.capitalize(grVariable.getNameIdentifierGroovy().getText()));

      text.append("(");
      text.append(type);
      text.append(" ");
      text.append("new").append(StringUtil.capitalize(grVariable.getNameIdentifierGroovy().getText()));
      text.append(")");
      text.append(" ");
      text.append("{");
      text.append("return;");
      text.append("}");

      text.append(";");
      text.append("\n");
    }
  }

  private void writeRunMethod(StringBuffer text) {
    text.append("public Object run() {\n" +
        "           return null;\n" +
        "        }");
  }

  private void writeConstructor(StringBuffer text, GrMethod constructor) {
    GrConstructorDefinitionImpl constrDefinition = (GrConstructorDefinitionImpl) constructor;

    writeMethodModifiers(text, constrDefinition.getModifierList(), JAVA_MODIFIERS);

    text.append(" ");

    /************* name **********/
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
      paramType = getResolvedType(paramTypeElement);

      text.append(paramType);
      text.append(" ");
      text.append(parameter.getName());

      i++;
    }
    text.append(")");

    /************* body **********/
    text.append("{");
    PsiParameterList list = constructor.getParameterList();
    PsiParameter[] parameters = list.getParameters();

    GrConstructorInvocation grConstructorInvocation = constrDefinition.getConstructorInvocation();
    if (grConstructorInvocation != null && grConstructorInvocation.isSuperCall()) {
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
    text.append("}");
  }

  private void writeVariableDeclarations(StringBuffer text, GrVariableDeclaration variableDeclaration) {
    GrTypeElement varTypeElement = variableDeclaration.getTypeElementGroovy();
    String varQualifiedTypeName = getResolvedType(varTypeElement);

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

      writeVariableDefinitionModifiers(text, modifierList, JAVA_MODIFIERS);

      //type
      text.append(varQualifiedTypeName);
      text.append(" ");

      //var name
      text.append(variable.getName());
      text.append(" = ");

      text.append(initValueToText);
      text.append(";\n");
      i++;
    }

  }

  private void writeMethod(StringBuffer text, GrMethod method, boolean isIntefraceMethod) {
    /************* type and name **********/
    GrTypeElement typeElement = method.getReturnTypeElementGroovy();
    String qualifiedTypeName = getResolvedType(typeElement);

    PsiModifierList modifierList = method.getModifierList();

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
      paramType = getResolvedType(paramTypeElement);

      text.append(paramType);
      text.append(" ");
      text.append(parameter.getName());

      i++;
    }
    text.append(")");

    if (!isIntefraceMethod) {
      /************* body **********/
      text.append("{");
      text.append("return ");

      if (typesToInitialValues.containsKey(qualifiedTypeName))
        text.append(typesToInitialValues.get(qualifiedTypeName));
      else
        text.append("null");

      text.append(";");

      text.append("}");
    } else {
      text.append(";");
    }

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

  private String getResolvedType(GrTypeElement typeElement) {
    if (typeElement == null) {
      return "java.lang.Object";
    } else {
      return computeTypeText(typeElement.getType());
    }
  }

  private String computeTypeText(final PsiType type) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        return computeInner(type);
      }
    });
  }

  private String computeInner(PsiType type) {
    if (type instanceof PsiArrayType) {
      return computeInner(((PsiArrayType) type).getComponentType()) + "[]";
    }
    
    String canonicalText = type.getCanonicalText();
    return canonicalText != null ? canonicalText : type.getPresentableText();
  }

  private void createGeneratedFile(StringBuffer text, String outputDir, String prefix, String generatedItemPath, GroovyFile myGroovyFile) {
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
    final Module myModule;
    public int myHashCode;

    public GenerationItemImpl(String path, Module module, ValidityState state) {
      myModule = module;
      myState = state;
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
  }
}
