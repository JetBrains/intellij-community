package org.jetbrains.plugins.groovy.compiler.geterator;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.FinalWrapper;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclarations;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrConstructor;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 03.05.2007
 */
public class GroovyToJavaGenerator implements SourceGeneratingCompiler {

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

  public GenerationItem[] getGenerationItems(CompileContext context) {
    VirtualFile[] files = context.getProjectCompileScope().getFiles(GroovyFileType.GROOVY_FILE_TYPE, true);

    List<GenerationItem> generationItems = new ArrayList<GenerationItem>();
    GenerationItem item;
    for (VirtualFile file : files) {
      final GroovyFile myPsiFile = findPsiFile(file);

      final FinalWrapper<GrTopStatement[]> statementsWrapper = new FinalWrapper<GrTopStatement[]>();
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          statementsWrapper.myValue = myPsiFile.getTopStatements();
        }
      });

      GrTopStatement[] statements = statementsWrapper.myValue;

      boolean needCreateTopLevelClass = !needsCreateClassFromFileName(statements);

      //top level class
      VirtualFile virtualFile;
      if (needCreateTopLevelClass) {
        String prefix = getJavaClassPackage(statements);
        virtualFile = myPsiFile.getVirtualFile();
        assert virtualFile != null;
        //todo: use parent directory in path if needs
        generationItems.add(new GenerationItemImpl(prefix + virtualFile.getNameWithoutExtension() + "." + "java", context.getModuleByFile(virtualFile), null));
      }

      final FinalWrapper<GrTypeDefinition[]> typeDefWrapper = new FinalWrapper<GrTypeDefinition[]>();
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          typeDefWrapper.myValue = myPsiFile.getTypeDefinitions();
        }
      });

      GrTypeDefinition[] typeDefinitions = typeDefWrapper.myValue;

      for (GrTypeDefinition typeDefinition : typeDefinitions) {
        item = new GenerationItemImpl(getJavaClassPackage(statements) + typeDefinition.getNameIdentifierGroovy().getText() + "." + "java", context.getModuleByFile(file), null);
        generationItems.add(item);
      }
    }
    return generationItems.toArray(new GenerationItem[0]);
  }

  public GenerationItem[] generate(CompileContext context, GenerationItem[] itemsToGenerate, VirtualFile outputRootDirectory) {

    VirtualFile[] children = outputRootDirectory.getChildren();
    for (final VirtualFile child : children) {

      ApplicationManager.getApplication().invokeAndWait(new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              try {
                child.delete(this);
              } catch (IOException e) {
                e.printStackTrace();
              }
            }
          });
        }
      }, ModalityState.NON_MODAL);

    }

    VirtualFile[] files = context.getProjectCompileScope().getFiles(GroovyFileType.GROOVY_FILE_TYPE, true);

    List<GenerationItem> generatedItems = new ArrayList<GenerationItem>();
    Map<String, GenerationItem> myPathsToItemsMap = new HashMap<String, GenerationItem>();

    for (GenerationItem item : itemsToGenerate) {
      myPathsToItemsMap.put(item.getPath(), item);
    }

    for (VirtualFile groovyFile : files) {
      //generate java classes form groovy source files
      VirtualFile itemFile = VirtualFileManager.getInstance().findFileByUrl(groovyFile.getUrl());
      assert itemFile != null;

      List<String> generatedJavaFilesRelPaths = generateItems(context, itemFile, outputRootDirectory);
      for (String relPath : generatedJavaFilesRelPaths) {
        GenerationItem generationItem = myPathsToItemsMap.get(relPath);
        if (generationItem != null) generatedItems.add(generationItem);
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
  private List<String> generateItems(CompileContext context, final VirtualFile item, final VirtualFile outputRootDirectory) {
    GroovyFile myPsiFile = findPsiFile(item);
    final Project project = VfsUtil.guessProjectForFile(item);
    assert project != null;

    Module module = ModuleUtil.findModuleForFile(item, project);

    List<String> gemeratedJavaFilesRelPaths = generate(context, myPsiFile, module, outputRootDirectory);
    assert gemeratedJavaFilesRelPaths != null;

    return gemeratedJavaFilesRelPaths;
  }

  private List<String> generate(CompileContext context, final GroovyFile myPsiFile, Module module, VirtualFile outputRootDirectory) {
    List<String> generatedItemsRelativePaths = new ArrayList<String>();

    final StringBuffer text = new StringBuffer();

    final FinalWrapper<GrTopStatement[]> statementsWrapper = new FinalWrapper<GrTopStatement[]>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        statementsWrapper.myValue = myPsiFile.getTopStatements();
      }
    });

    GrTopStatement[] statements = statementsWrapper.myValue;

    //there is member on top level
    boolean isOnlyInnerTypeDef = needsCreateClassFromFileName(statements);

    if (statements.length != 0 && !isOnlyInnerTypeDef) {
      VirtualFile virtualFile = myPsiFile.getVirtualFile();
      assert virtualFile != null;
      String typeDefinitionName = virtualFile.getNameWithoutExtension();

      String topLevelGeneratedItemPath = createJavaSourceFile(context, outputRootDirectory, myPsiFile, text, typeDefinitionName, statements);
      generatedItemsRelativePaths.add(topLevelGeneratedItemPath);
    }

    final FinalWrapper<GrTypeDefinition[]> typeDefWrapper = new FinalWrapper<GrTypeDefinition[]>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        typeDefWrapper.myValue = myPsiFile.getTypeDefinitions();
      }
    });

    GrTypeDefinition[] typeDefinitions = typeDefWrapper.myValue;

    String generatedItemPath;
    for (final GrTypeDefinition typeDefinition : typeDefinitions) {
      text.setLength(0);

      final FinalWrapper<GrTopStatement[]> topStatementsWrapper = new FinalWrapper<GrTopStatement[]>();
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          topStatementsWrapper.myValue = typeDefinition.getStatements();
        }
      });

      GrTopStatement[] topStatements = topStatementsWrapper.myValue;
      PsiElement element = typeDefinition.getNameIdentifierGroovy();

      generatedItemPath = createJavaSourceFile(context, outputRootDirectory, myPsiFile, text, element.getText(), topStatements);
      generatedItemsRelativePaths.add(generatedItemPath);
    }

    return generatedItemsRelativePaths;
  }

  private String getJavaClassPackage(GrTopStatement[] statements) {
    String prefix = "";
    if (statements.length > 0 && statements[0] instanceof GrPackageDefinition) {
      prefix = ((GrPackageDefinition) statements[0]).getPackageName();
      prefix = prefix.replace(".", File.separator);
      prefix += File.separator;
    }
    return prefix;
  }

  private String createJavaSourceFile(CompileContext context, VirtualFile outputRootDirectory, GroovyFile myPsiFile, StringBuffer text, String typeDefinitionName, GrTopStatement[] statements) {
    String prefix = getJavaClassPackage(statements);
    writeTypeDefinition(text, typeDefinitionName, statements);

    VirtualFile virtualFile = myPsiFile.getVirtualFile();
    assert virtualFile != null;
    String generatedFileRelativePath = typeDefinitionName + "." + "java";
    createGeneratedFile(context, text, outputRootDirectory.getPath(), prefix, generatedFileRelativePath, myPsiFile);
    return generatedFileRelativePath;
  }

  private boolean needsCreateClassFromFileName(GrTopStatement[] statements) {
    boolean isOnlyInnerTypeDef = true;
    for (GrTopStatement statement : statements) {
      if (!(statement instanceof GrTypeDefinition || statement instanceof GrImportStatement))
        isOnlyInnerTypeDef &= false;
    }
    return isOnlyInnerTypeDef;
  }

  private void writeTypeDefinition(StringBuffer text, String typeDefinitionName, GrTopStatement[] statements) {
    Map<String, String> classNameToQualifiedName = new HashMap<String, String>();
    List<GrImportStatement> importStatements = new ArrayList<GrImportStatement>();

    for (GrTopStatement statement : statements) {
      if (statement instanceof GrImportStatement)
        importStatements.add((GrImportStatement) statement);
    }

    fillClassNameToQualifiedNameMap(importStatements.toArray(new GrImportStatement[0]), classNameToQualifiedName);

    text.append("class");
    text.append(" ");

    text.append(typeDefinitionName);
    text.append(" ");
    text.append("{");

    for (GrTopStatement statement : statements) {
      if (statement instanceof GrConstructor) {
        writeConstructor(classNameToQualifiedName, text, statement);
        text.append("\n");
      }

      if (statement instanceof GrMethod) {
        writeMethod(classNameToQualifiedName, text, statement);
        text.append("\n");
      }

      if (statement instanceof GrVariableDeclarations) {
        writeVariableDeclarations(classNameToQualifiedName, text, statement);
      }
    }

    text.append("}");
  }

  private void writeConstructor(Map<String, String> classNameToQualifiedName, StringBuffer text, GrTopStatement topStatement) {
    GrMethod method = (GrMethod) topStatement;

    /************* name **********/
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
      paramType = getResolvedType(classNameToQualifiedName, paramTypeElement);

      text.append(paramType);
      text.append(" ");
      text.append(parameter.getName());

      i++;
    }
    text.append(")");

    /************* body **********/
    text.append("{");
    text.append("}");
  }

  private void writeVariableDeclarations(Map<String, String> classNameToQualifiedName, StringBuffer text, GrTopStatement topStatement) {
    GrVariableDeclarations variableDeclarations = (GrVariableDeclarations) topStatement;

    GrTypeElement varTypeElement = variableDeclarations.getTypeElementGroovy();
    String varQualifiedTypeName = getResolvedType(classNameToQualifiedName, varTypeElement);

    //append qualified type name
    text.append(varQualifiedTypeName);
    text.append(" ");

    //append method name
    text.append(variableDeclarations.getName());

    text.append(" = ");
    if (typesToInitialValues.containsKey(varQualifiedTypeName))
      text.append(typesToInitialValues.get(varQualifiedTypeName));
    else
      text.append("null");

    text.append(";");
  }

  private void fillClassNameToQualifiedNameMap(GrImportStatement[] importStatements, Map<String, String> classNameToQualifiedName) {
    classNameToQualifiedName.put("Object", "java.lang.Object");

    String alias;
    for (GrImportStatement importStatement : importStatements) {
      alias = importStatement.getImportedName();
      if (alias != null)
        classNameToQualifiedName.put(alias, importStatement.getImportReference().getQualifier().getText());
    }


    classNameToQualifiedName.putAll(typesToInitialValues);
  }

  private void writeMethod(Map<String, String> classNameToQualifiedName, StringBuffer text, GrTopStatement topStatement) {
    GrMethod method = (GrMethod) topStatement;

    /************* type and name **********/
    GrTypeElement typeElement = method.getReturnTypeElementGroovy();
    String qualifiedTypeName = getResolvedType(classNameToQualifiedName, typeElement);

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
      paramType = getResolvedType(classNameToQualifiedName, paramTypeElement);
//      paramTypeElement = parameter.getType();

//      if (paramTypeElement == null) paramType = "Object";
//      else paramType = paramTypeElement.getText();

      text.append(paramType);
      text.append(" ");
      text.append(parameter.getName());

      i++;
    }
    text.append(")");

    /************* body **********/
    text.append("{");

    if (typesToInitialValues.containsKey(qualifiedTypeName))
      text.append(typesToInitialValues.get(qualifiedTypeName));
    else
      text.append("return null;");

    text.append("}");

  }

  private String getResolvedType(Map<String, String> classNameToQualifiedName, GrTypeElement typeElement) {
    String methodType;
    if (typeElement == null) {
      methodType = "Object";
    } else {
      final PsiType type = typeElement.getType();

      final FinalWrapper<String> resolverTypeWrapper = new FinalWrapper<String>();
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          resolverTypeWrapper.myValue = type.getCanonicalText();
        }
      });

      String resolvedType = resolverTypeWrapper.myValue;
      methodType = resolvedType == null ? "<dimaskin>" : resolvedType;
    }

    String qualifiedTypeName = classNameToQualifiedName.get(methodType);

    if (qualifiedTypeName == null) qualifiedTypeName = methodType;
    return qualifiedTypeName;
  }

  private void createGeneratedFile(CompileContext context, StringBuffer text, String outputDir, String prefix, String generatedItemPath, GroovyFile myGroovyFile) {
    assert prefix != null;

    String prefixWithoutSeparator = prefix;

    if (!"".equals(prefix)) {
      prefixWithoutSeparator = prefix.substring(0, prefix.length() - File.separator.length());
      new File(outputDir, prefixWithoutSeparator).mkdirs();
    }

    File myFile;
    if (!"".equals(prefix))
      myFile = new File(outputDir + File.separator + prefixWithoutSeparator, generatedItemPath);
    else
      myFile = new File(outputDir, generatedItemPath);

    if (myFile.exists()) {
      VirtualFile virtualFile = myGroovyFile.getVirtualFile();
      assert virtualFile != null;
      String url = virtualFile.getUrl();

      context.addMessage(
          CompilerMessageCategory.ERROR,
          GroovyBundle.message("Class") + " " + myFile.getName() + " " + GroovyBundle.message("already.exist"),
          url,
          -1,
          -1);
      return;
    }

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
      System.out.println("");
    }
  }

//  private void createGenerationItem(GroovyFile myPsiFile, VirtualFile outputRootDirectory, final StringBuffer text) {
//    try {
//      final VirtualFile newFile = VfsUtil.createChildSequent(this, outputRootDirectory, myPsiFile.getName(), "java");
//      assert newFile != null;
//
//      ApplicationManager.getApplication().invokeLater(new Runnable() {
//        public void run() {
//          try {
//            newFile.getOutputStream(this).write(text.toString().getBytes());
//          } catch (IOException e) {
//            e.printStackTrace();
//          }
//        }
//      });
//
//    } catch (IOException e) {
//      e.printStackTrace();
//    }
//  }

  @NotNull
  public String getDescription() {
    return "Groovy to java source code generator";
  }

  //TODO
  public boolean validateConfiguration(CompileScope scope) {
//    scope.getFiles(GroovyFileType.GROOVY_FILE_TYPE, true);
    return true;
  }

  //todo: change it
  public ValidityState createValidityState(DataInputStream is) throws IOException {
    return new ValidityState() {
      public boolean equalsTo(ValidityState otherState) {
        return this.equals(otherState);
      }

      public void save(DataOutputStream os) throws IOException {
      }
    };
  }

  class GenerationItemImpl implements GenerationItem {
    final String myRelativePath;
    final String myPath;
    ValidityState myState;
    final Module myModule;

    public GenerationItemImpl(String myPath, Module myModule, ValidityState myState) {
      this.myPath = myPath;
      this.myModule = myModule;
      this.myState = myState;
      this.myRelativePath = new File(myPath).getName();
    }

    public String getPath() {
      return myRelativePath;
    }

    public ValidityState getValidityState() {
      return myState;
    }

    public Module getModule() {
      return myModule;
    }

    public String getMyPath() {
      return myPath;
    }
  }
}
