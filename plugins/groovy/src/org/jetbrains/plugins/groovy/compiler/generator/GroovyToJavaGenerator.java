/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.compiler.generator;

import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.TimestampValidityState;
import com.intellij.openapi.compiler.ValidityState;
import com.intellij.openapi.compiler.options.ExcludedEntriesConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.compiler.GroovyCompilerConfiguration;
import org.jetbrains.plugins.groovy.lang.psi.GrClassSubstitutor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrConstructor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil;
import org.jetbrains.plugins.groovy.util.GroovyUtils;
import org.jetbrains.plugins.groovy.util.containers.CharTrie;

import java.io.*;
import java.util.*;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 03.05.2007
 */
public class GroovyToJavaGenerator {
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
  };

  private static final CharSequence PREFIX_SEPARATOR = "/";
  private final CompileContext myContext;
  private final List<VirtualFile> myAllToCompile;
  private final Project myProject;

  public GroovyToJavaGenerator(Project project, CompileContext context, List<VirtualFile> allToCompile) {
    myProject = project;
    myContext = context;
    myAllToCompile = allToCompile;
  }

  public GenerationItem[] getGenerationItems(CompileContext context) {
    if (GroovyCompilerConfiguration.getInstance(myProject).isUseGroovycStubs()) {
      return new GenerationItem[0];
    }

    List<GenerationItem> generationItems = new ArrayList<GenerationItem>();
    GenerationItem item;
    final CompilerManager compilerManager = CompilerManager.getInstance(myProject);
    final CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(myProject);
    final ExcludedEntriesConfiguration excluded = GroovyCompilerConfiguration.getExcludeConfiguration(myProject);
    for (VirtualFile file : getGroovyFilesToGenerate(context)) {
      if (compilerManager.isExcludedFromCompilation(file)) continue;
      if (excluded.isExcluded(file)) continue;
      if (compilerConfiguration.isResourceFile(file)) continue;

      final Module module = getModuleByFile(context, file);
      if (!GroovyUtils.isSuitableModule(module)) {
        continue;
      }

      final GroovyFileBase psiFile = findPsiFile(file);
      GrTopStatement[] statements = getTopStatementsInReadAction(psiFile);

      boolean needCreateTopLevelClass = !needsCreateClassFromFileName(statements);

      String prefix = "";
      if (statements.length > 0 && statements[0] instanceof GrPackageDefinition) {
        prefix = getJavaClassPackage((GrPackageDefinition) statements[0]);
      }

      //top level class
      if (needCreateTopLevelClass) {
        generationItems.add(new GenerationItem(prefix + file.getNameWithoutExtension() + "." + "java", module, new TimestampValidityState(file.getTimeStamp()),
                                               file));
      }

      GrTypeDefinition[] typeDefinitions = ApplicationManager.getApplication().runReadAction(new Computable<GrTypeDefinition[]>() {
        public GrTypeDefinition[] compute() {
          return psiFile.getTypeDefinitions();
        }
      });

      for (GrTypeDefinition typeDefinition : typeDefinitions) {
        item = new GenerationItem(prefix + typeDefinition.getName() + "." + "java", module, new TimestampValidityState(file.getTimeStamp()),
                                  file);
        generationItems.add(item);
      }
    }
    return generationItems.toArray(new GenerationItem[generationItems.size()]);
  }

  protected Module getModuleByFile(CompileContext context, VirtualFile file) {
    return context.getModuleByFile(file);
  }

  protected VirtualFile[] getGroovyFilesToGenerate(final CompileContext context) {
    final HashSet<VirtualFile> set = new HashSet<VirtualFile>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        ContainerUtil.addAll(set, context.getProjectCompileScope().getFiles(GroovyFileType.GROOVY_FILE_TYPE, true));
      }
    });

    return VfsUtil.toVirtualFileArray(set);
  }

  public GenerationItem[] generate(GenerationItem[] itemsToGenerate, VirtualFile outputRootDirectory) {
    List<GenerationItem> generatedItems = new ArrayList<GenerationItem>();
    Map<String, GenerationItem> pathsToItemsMap = new HashMap<String, GenerationItem>();

    //puts items witch can be generated
    for (GenerationItem item : itemsToGenerate) {
      pathsToItemsMap.put(item.getPath(), item);
    }

    Set<VirtualFile> vFiles = new HashSet<VirtualFile>();
    for (GenerationItem item : itemsToGenerate) {
      vFiles.add(item.getVFile());
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
  public List<String> generateItems(final VirtualFile item, final VirtualFile outputRootDirectory) {
    ProgressIndicator indicator = getProcessIndicator();
    if (indicator != null) indicator.setText("Generating stubs for " + item.getName() + "...");

    if (LOG.isDebugEnabled()) {
      LOG.debug("Generating stubs for " + item.getName() + "...");
    }

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

    Set<String> classNames = new THashSet<String>();
    for (final GrTypeDefinition typeDefinition : file.getTypeDefinitions()) {
      classNames.add(typeDefinition.getName());
    }

    if (file.isScript()) {
      VirtualFile virtualFile = file.getVirtualFile();
      assert virtualFile != null;
      String fileDefinitionName = virtualFile.getNameWithoutExtension();
      if (!classNames.contains(StringUtil.capitalize(fileDefinitionName)) &&
          !classNames.contains(StringUtil.decapitalize(fileDefinitionName))) {
        final PsiClass scriptClass = file.getScriptClass();
        if (scriptClass != null) {
          generatedItemsRelativePaths.add(createJavaSourceFile(outputRootDirectory, scriptClass, packageDefinition));
        }
      }
    }

    for (final GrTypeDefinition typeDefinition : file.getTypeDefinitions()) {
      generatedItemsRelativePaths.add(createJavaSourceFile(outputRootDirectory, GrClassSubstitutor.getSubstitutedClass(typeDefinition), packageDefinition));
    }

    return generatedItemsRelativePaths;
  }

  private static String getJavaClassPackage(@Nullable GrPackageDefinition packageDefinition) {
    if (packageDefinition == null) return "";

    String prefix = packageDefinition.getPackageName();
    prefix = prefix.replace(".", PREFIX_SEPARATOR);
    prefix += PREFIX_SEPARATOR;

    return prefix;
  }

  private String createJavaSourceFile(VirtualFile outputRootDirectory, @NotNull PsiClass typeDefinition, GrPackageDefinition packageDefinition) {
    StringBuffer text = new StringBuffer();
    writeTypeDefinition(text, typeDefinition, packageDefinition, true);

    String prefix = getJavaClassPackage(packageDefinition);
    String outputDir = outputRootDirectory.getPath();
    String fileName = typeDefinition.getName() + "." + "java";

    String prefixWithoutSeparator = prefix;

    if (!"".equals(prefix)) {
      prefixWithoutSeparator = prefix.substring(0, prefix.length() - PREFIX_SEPARATOR.length());
      new File(outputDir, prefixWithoutSeparator).mkdirs();
    }

    File myFile;
    if (!"".equals(prefix))
      myFile = new File(outputDir + File.separator + prefixWithoutSeparator, fileName);
    else
      myFile = new File(outputDir, fileName);

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
    return prefix + fileName;
  }

  private static GrTopStatement[] getTopStatementsInReadAction(final GroovyFileBase myPsiFile) {
    if (myPsiFile == null) return new GrTopStatement[0];

    return ApplicationManager.getApplication().runReadAction(new Computable<GrTopStatement[]>() {
      public GrTopStatement[] compute() {
        return myPsiFile.getTopStatements();
      }
    });
  }

  private static boolean needsCreateClassFromFileName(GrTopStatement[] statements) {
    boolean isOnlyInnerTypeDef = true;
    for (GrTopStatement statement : statements) {
      if (!(statement instanceof GrTypeDefinition || statement instanceof GrImportStatement || statement instanceof GrPackageDefinition)) {
        isOnlyInnerTypeDef = false;
        break;
      }
    }
    return isOnlyInnerTypeDef;
  }

  private void writeTypeDefinition(StringBuffer text, @NotNull PsiClass typeDefinition,
                                   @Nullable GrPackageDefinition packageDefinition, boolean toplevel) {
    final boolean isScript = typeDefinition instanceof GroovyScriptClass;

    writePackageStatement(text, packageDefinition);

    boolean isEnum = typeDefinition.isEnum();
    boolean isAnnotationType = typeDefinition.isAnnotationType();
    boolean isInterface = !isAnnotationType && typeDefinition.isInterface();
    boolean isClassDef = !isInterface && !isEnum && !isAnnotationType && !isScript;

    writeClassModifiers(text, typeDefinition.getModifierList(), typeDefinition.isInterface(), toplevel);

    if (isInterface) text.append("interface");
    else if (isEnum) text.append("enum");
    else if (isAnnotationType) text.append("@interface");
    else text.append("class");

    text.append(" ").append(typeDefinition.getName());

    appendTypeParameters(text, typeDefinition);

    text.append(" ");

    if (isScript) {
      text.append("extends groovy.lang.Script ");
    }
    else if (!isEnum && !isAnnotationType) {
      final PsiClassType[] extendsClassesTypes = typeDefinition.getExtendsListTypes();

      if (extendsClassesTypes.length > 0) {
        text.append("extends ").append(getTypeText(extendsClassesTypes[0], typeDefinition, false)).append(" ");
      }
      PsiClassType[] implementsTypes = typeDefinition.getImplementsListTypes();

      if (implementsTypes.length > 0) {
        text.append(isInterface ? "extends " : "implements ");
        int i = 0;
        while (i < implementsTypes.length) {
          if (i > 0) text.append(", ");
          text.append(getTypeText(implementsTypes[i], typeDefinition, false)).append(" ");
          i++;
        }
      }
    }

    text.append("{");

    if (isEnum) {
      writeEnumConstants(text, (GrEnumTypeDefinition)typeDefinition);
    }

    writeAllMethods(text, collectMethods(typeDefinition, isClassDef), typeDefinition);

    if (typeDefinition instanceof GrTypeDefinition) {
      for (GrMembersDeclaration declaration : ((GrTypeDefinition)typeDefinition).getMemberDeclarations()) {
        if (declaration instanceof GrVariableDeclaration) {
          writeVariableDeclarations(text, (GrVariableDeclaration)declaration);
        }
      }
    }
    for (PsiClass inner : typeDefinition.getInnerClasses()) {
      writeTypeDefinition(text, inner, null, false);
      text.append("\n");
    }

    text.append("}");
  }

  private void writeAllMethods(StringBuffer text, List<PsiMethod> methods, PsiClass aClass) {
    Set<MethodSignature> methodSignatures = new HashSet<MethodSignature>();
    for (PsiMethod method : methods) {
      if (LightMethodBuilder.isLightMethod(method, GrClassImplUtil.SYNTHETIC_METHOD_IMPLEMENTATION)) {
        continue;
      }

      if (method instanceof GrConstructor) {
        writeConstructor(text, (GrConstructor)method, aClass.isEnum());
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
          MethodSignature signature =
            MethodSignatureUtil.createMethodSignature(method.getName(), parameterTypes, method.getTypeParameters(), PsiSubstitutor.EMPTY);
          if (methodSignatures.add(signature)) {
            writeMethod(text, method, parametersCopy);
          }

          PsiParameter parameter = parameters[i];
          if (!(parameter instanceof GrParameter) || !((GrParameter)parameter).isOptional()) break;
          parameterTypes = ArrayUtil.remove(parameterTypes, parameterTypes.length - 1);
          parametersCopy = ArrayUtil.remove(parametersCopy, parametersCopy.length - 1);
        }
      }
      else {
        MethodSignature signature = method.getSignature(PsiSubstitutor.EMPTY);
        if (methodSignatures.add(signature)) {
          writeMethod(text, method, parameters);
        }
      }
    }
  }

  private List<PsiMethod> collectMethods(PsiClass typeDefinition, boolean classDef) {
    List<PsiMethod> methods = new ArrayList<PsiMethod>();
    ContainerUtil.addAll(methods, typeDefinition.getMethods());
    if (classDef) {
      final Collection<MethodSignature> toOverride = OverrideImplementUtil.getMethodSignaturesToOverride(typeDefinition);
      for (MethodSignature signature : toOverride) {
        if (signature instanceof MethodSignatureBackedByPsiMethod) {
          final PsiMethod method = ((MethodSignatureBackedByPsiMethod)signature).getMethod();
          final PsiClass baseClass = method.getContainingClass();
          if (isAbstractInJava(method) && baseClass != null && typeDefinition.isInheritor(baseClass, true)) {
            final LightMethodBuilder builder = new LightMethodBuilder(method.getManager(), method.getName());
            final PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, typeDefinition, PsiSubstitutor.EMPTY);
            for (PsiParameter parameter : method.getParameterList().getParameters()) {
              builder.addParameter(parameter.getName(), substitutor.substitute(parameter.getType()));
            }
            builder.setReturnType(substitutor.substitute(method.getReturnType()));
            for (String modifier : JAVA_MODIFIERS) {
              if (method.hasModifierProperty(modifier)) {
                builder.addModifier(modifier);
              }
            }
            methods.add(builder);
          }
        }
      }

      final PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();
      methods.add(factory.createMethodFromText("public groovy.lang.MetaClass getMetaClass() {}", null));
      methods.add(factory.createMethodFromText("public void setMetaClass(groovy.lang.MetaClass mc) {}", null));
      methods.add(factory.createMethodFromText("public Object invokeMethod(String name, Object args) {}", null));
      methods.add(factory.createMethodFromText("public Object getProperty(String propertyName) {}", null));
      methods.add(factory.createMethodFromText("public void setProperty(String propertyName, Object newValue) {}", null));
    }
    return methods;
  }

  private static boolean isAbstractInJava(PsiMethod method) {
    if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return true;
    }

    final PsiClass psiClass = method.getContainingClass();
    return psiClass != null && GrClassSubstitutor.getSubstitutedClass(psiClass).isInterface();
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
          text.append(" extends ");
          for (int j = 0; j < extendsListTypes.length; j++) {
            if (j > 0) text.append(" & ");
            text.append(getTypeText(extendsListTypes[j], typeParameterListOwner, false));
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
        writeStubConstructorInvocation(text, constructor, PsiSubstitutor.EMPTY);
        text.append(")");
      }

      GrTypeDefinitionBody block = enumConstant.getAnonymousBlock();
      if (block != null) {
        text.append("{\n");
        for (PsiMethod method : block.getMethods()) {
          writeMethod(text, method, method.getParameterList().getParameters());
        }
        text.append("}");
      }
    }
    text.append(";");
  }

  private void writeStubConstructorInvocation(StringBuffer text, PsiMethod constructor, PsiSubstitutor substitutor) {
    final PsiParameter[] superParams = constructor.getParameterList().getParameters();
    for (int j = 0; j < superParams.length; j++) {
      if (j > 0) text.append(", ");
      String typeText = getTypeText(substitutor.substitute(superParams[j].getType()), null, false);
      text.append("(").append(typeText).append(")").append(getDefaultValueText(typeText));
    }
  }

  private static void writePackageStatement(StringBuffer text, GrPackageDefinition packageDefinition) {
    if (packageDefinition != null) {
      text.append("package ");
      text.append(packageDefinition.getPackageName());
      text.append(";");
      text.append("\n");
      text.append("\n");
    }
  }

  private void writeConstructor(final StringBuffer text, final GrConstructor constructor, boolean isEnum) {
    text.append("\n");
    text.append("  ");
    if (!isEnum) {
      text.append("public ");
      //writeMethodModifiers(text, constructor.getModifierList(), JAVA_MODIFIERS);
    }

    /************* name **********/
    //append constructor name
    text.append(constructor.getName());

    /************* parameters **********/
    GrParameter[] parameterList = constructor.getParameters();

    writeParameterList(text, parameterList);

    final Set<String> throwsTypes = collectThrowsTypes(constructor, new THashSet<PsiMethod>());
    if (!throwsTypes.isEmpty()) {
      text.append("throws ").append(StringUtil.join(throwsTypes, ", ")).append(" ");
    }

    /************* body **********/

    text.append("{\n");
    final GrConstructorInvocation invocation = constructor.getChainingConstructorInvocation();
    if (invocation != null) {
      final GroovyResolveResult resolveResult = resolveChainingConstructor(constructor);
      if (resolveResult != null) {
        text.append("    ");
        text.append(invocation.isSuperCall() ? "super(" : "this(");
        writeStubConstructorInvocation(text, (PsiMethod) resolveResult.getElement(), resolveResult.getSubstitutor());
        text.append(");");
      }
    }

    text.append("\n  }\n");
  }

  private Set<String> collectThrowsTypes(GrConstructor constructor, Set<PsiMethod> visited) {
    final GroovyResolveResult resolveResult = resolveChainingConstructor(constructor);
    if (resolveResult == null) {
      return Collections.emptySet();
    }


    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    final PsiMethod chainedConstructor = (PsiMethod)resolveResult.getElement();
    assert chainedConstructor != null;

    if (!visited.add(chainedConstructor)) {
      return Collections.emptySet();
    }

    final Set<String> result = CollectionFactory.newTroveSet();
    for (PsiClassType type : chainedConstructor.getThrowsList().getReferencedTypes()) {
      result.add(getTypeText(substitutor.substitute(type), null, false));
    }

    if (chainedConstructor instanceof GrConstructor) {
      result.addAll(collectThrowsTypes((GrConstructor)chainedConstructor, visited));
    }
    return result;
  }

  @Nullable
  private static GroovyResolveResult resolveChainingConstructor(GrConstructor constructor) {
    final GrConstructorInvocation constructorInvocation = constructor.getChainingConstructorInvocation();
    if (constructorInvocation == null) {
      return null;
    }

    GroovyResolveResult resolveResult = constructorInvocation.resolveConstructorGenerics();
    if (resolveResult.getElement() != null) {
      return resolveResult;
    }

    final GroovyResolveResult[] results = constructorInvocation.multiResolveConstructor();
    if (results.length > 0) {
      int i = 0;
      while (results.length > i+1) {
        final PsiMethod candidate = (PsiMethod)results[i].getElement();
        final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(constructor.getProject()).getResolveHelper();
        if (candidate != null && candidate != constructor && resolveHelper.isAccessible(candidate, constructorInvocation, null)) {
          break;
        }
        i++;
      }
      return results[i];
    }
    return null;
  }

  private static String getDefaultValueText(String typeCanonicalText) {
    final String result = typesToInitialValues.get(typeCanonicalText);
    if (result == null) return "null";
    return result;
  }

  private void writeVariableDeclarations(StringBuffer text, GrVariableDeclaration variableDeclaration) {
    GrTypeElement typeElement = variableDeclaration.getTypeElementGroovy();
    final String type = typeElement == null ? CommonClassNames.JAVA_LANG_OBJECT : getTypeText(typeElement.getType(), typeElement, false);
    final String initializer = getDefaultValueText(type);

    final GrModifierList modifierList = variableDeclaration.getModifierList();
    final PsiNameHelper nameHelper = JavaPsiFacade.getInstance(variableDeclaration.getProject()).getNameHelper();
    for (final GrVariable variable : variableDeclaration.getVariables()) {
      String name = variable.getName();
      if (!nameHelper.isIdentifier(name)) {
        continue; //does not have a java image
      }

      text.append("\n  ");
      writeFieldModifiers(text, modifierList, JAVA_MODIFIERS);

      //type
      text.append(type).append(" ").append(name).append(" = ").append(initializer).append(";\n");
    }
  }

  private void writeMethod(StringBuffer text, PsiMethod method, final PsiParameter[] parameters) {
    if (method == null) return;
    String name = method.getName();
    if (!JavaPsiFacade.getInstance(method.getProject()).getNameHelper().isIdentifier(name))
      return; //does not have a java image

    boolean isAbstract = isAbstractInJava(method);

    PsiModifierList modifierList = method.getModifierList();

    text.append("\n");
    text.append("  ");
    writeMethodModifiers(text, modifierList, JAVA_MODIFIERS);
    if (method.hasTypeParameters()) {
      appendTypeParameters(text, method);
      text.append(" ");
    }

    //append return type
    PsiType retType = method.getReturnType();
    if (retType == null) retType = TypesUtil.getJavaLangObject(method);

    text.append(getTypeText(retType, method, false));
    text.append(" ");

    text.append(name);

    writeParameterList(text, parameters);

    if (!isAbstract) {
      /************* body **********/
      text.append("{\n");
      text.append("    return ");

      text.append(getDefaultValueText(getTypeText(retType, method, false)));

      text.append(";");

      text.append("\n  }");
    } else {
      text.append(";");
    }
    text.append("\n");
  }

  private void writeParameterList(StringBuffer text, PsiParameter[] parameters) {
    text.append("(");

    //writes myParameters
    int i = 0;
    while (i < parameters.length) {
      PsiParameter parameter = parameters[i];
      if (parameter == null) continue;

      if (i > 0) text.append(", ");  //append ','

      text.append(getTypeText(parameter.getType(), parameter, i == parameters.length - 1));
      text.append(" ");
      text.append(parameter.getName());

      i++;
    }
    text.append(")");
    text.append(" ");
  }

  private static boolean writeMethodModifiers(StringBuffer text, PsiModifierList modifierList, String[] modifiers) {
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

  private static void writeFieldModifiers(StringBuffer text, GrModifierList modifierList, String[] modifiers) {
    for (String modifierType : modifiers) {
      if (modifierList.hasModifierProperty(modifierType)) {
        text.append(modifierType);
        text.append(" ");
      }
    }
  }

  private static void writeClassModifiers(StringBuffer text,
                                             @Nullable PsiModifierList modifierList, boolean isInterface, boolean toplevel) {
    if (modifierList == null || modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
      text.append("public ");
    }

    if (modifierList != null) {
      List<String> allowedModifiers = new ArrayList<String>();
      allowedModifiers.add(PsiModifier.FINAL);
      if (!toplevel) {
        allowedModifiers.addAll(Arrays.asList(PsiModifier.PROTECTED, PsiModifier.PRIVATE, PsiModifier.STATIC));
      }
      if (!isInterface) {
        allowedModifiers.add(PsiModifier.ABSTRACT);
      }

      for (String modifierType : allowedModifiers) {
        if (modifierList.hasModifierProperty(modifierType)) {
          text.append(modifierType).append(" ");
        }
      }
    }
  }

  private String getTypeText(@Nullable PsiType type, @Nullable final PsiElement context, boolean allowVarargs) {
    if (type instanceof PsiArrayType) {
      String componentText = getTypeText(((PsiArrayType)type).getComponentType(), context, false);
      if (allowVarargs && type instanceof PsiEllipsisType) {
        return componentText + "...";
      }
      return componentText + "[]";
    }

    if (type == null) {
      return CommonClassNames.JAVA_LANG_OBJECT;
    }

    if (type instanceof PsiClassType) {
      final PsiClass raw = ((PsiClassType)type).resolve();
      if (raw != null) {
        final String qname = getClassQualifiedName(raw, context);
        if (qname != null) {
          final PsiType[] parameters = ((PsiClassType)type).getParameters();
          if (parameters.length > 0) {
            return qname + "<" + StringUtil.join(parameters, new Function<PsiType, String>() {
              @Override
              public String fun(PsiType type) {
                return getTypeText(type, context, false);
              }
            }, ", ") + ">";
          }
          return qname;
        }
      }
    }

    String canonicalText = type.getCanonicalText();
    return canonicalText != null ? canonicalText : type.getPresentableText();
  }

  @Nullable
  private String getClassQualifiedName(PsiClass psiClass, @Nullable PsiElement context) {
    if (context != null) {
      psiClass = findAccessibleSuperClass(context, psiClass);
    }
    if (psiClass == null) {
      return null;
    }

    if (psiClass instanceof GrTypeDefinition) {
      if (!myAllToCompile.contains(psiClass.getContainingFile().getVirtualFile())) {
        final PsiClass container = psiClass.getContainingClass();
        if (container != null) {
          return getClassQualifiedName(container, null) + "$" + psiClass.getName();
        }
      }
    }
    return psiClass.getQualifiedName();
  }

  @Nullable
  private static PsiClass findAccessibleSuperClass(@NotNull PsiElement context, @NotNull PsiClass initialClass) {
    PsiClass curClass = initialClass;
    final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(context.getProject()).getResolveHelper();
    while (curClass != null && !resolveHelper.isAccessible(curClass, context, null)) {
      curClass = curClass.getSuperClass();
    }
    return curClass;
  }

  CharTrie myTrie = new CharTrie();

  public class GenerationItem {
    ValidityState myState;
    final Module myModule;
    public int myHashCode;
    private final VirtualFile myVFile;

    public GenerationItem(String path, Module module, ValidityState state, VirtualFile vFile) {
      myVFile = vFile;
      myModule = module;
      myState = state;
      myHashCode = myTrie.getHashCode(path);
    }

    public String getPath() {
      return myTrie.getString(myHashCode);
    }

    public Module getModule() {
      return myModule;
    }

    public VirtualFile getVFile() {
      return myVFile;
    }
  }
}
