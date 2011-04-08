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
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrClassSubstitutor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil;

import java.util.*;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 03.05.2007
 */
public class GroovyToJavaGenerator {
  private static final Map<String, String> typesToInitialValues = new HashMap<String, String>();
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.refactoring.convertToJava.GroovyToJavaGenerator");

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

  private final List<VirtualFile> myAllToCompile;
  private final Project myProject;

  private final boolean fullConversion;

  public GroovyToJavaGenerator(Project project, List<VirtualFile> allToCompile, boolean fullConversion) {
    myProject = project;
    myAllToCompile = allToCompile;
    this.fullConversion = fullConversion;
  }

  public Map<String, String> generateStubs(GroovyFile file) {
    GrTopStatement[] statements = getTopStatementsInReadAction(file);

    GrPackageDefinition packageDefinition = null;
    if (statements.length > 0 && statements[0] instanceof GrPackageDefinition) {
      packageDefinition = (GrPackageDefinition) statements[0];
    }

    Set<String> classNames = new THashSet<String>();
    for (final GrTypeDefinition typeDefinition : file.getTypeDefinitions()) {
      classNames.add(typeDefinition.getName());
    }

    final Map<String, String> output = new LinkedHashMap<String, String>();

    if (file.isScript()) {
      VirtualFile virtualFile = file.getVirtualFile();
      assert virtualFile != null;
      String fileDefinitionName = virtualFile.getNameWithoutExtension();
      if (!classNames.contains(StringUtil.capitalize(fileDefinitionName)) &&
          !classNames.contains(StringUtil.decapitalize(fileDefinitionName))) {
        final PsiClass scriptClass = file.getScriptClass();
        if (scriptClass != null) {
          generateClassStub(scriptClass, packageDefinition, output);
        }
      }
    }

    for (final GrTypeDefinition typeDefinition : file.getTypeDefinitions()) {
      generateClassStub(GrClassSubstitutor.getSubstitutedClass(typeDefinition), packageDefinition, output);
    }
    return output;
  }

  private static String getPackageDirectory(@Nullable GrPackageDefinition packageDefinition) {
    if (packageDefinition == null) return "";

    String prefix = packageDefinition.getPackageName();
    if (prefix == null) return "";

    return prefix.replace('.', '/') + '/';
  }

  private void generateClassStub(@NotNull PsiClass typeDefinition, GrPackageDefinition packageDefinition, Map<String, String> output) {
    StringBuilder text = new StringBuilder();
    try {
      writeTypeDefinition(text, typeDefinition, packageDefinition, true);


      final String fileName;
      if (fullConversion) {
        fileName = typeDefinition.getName() + ".java";
      }
      else {
        fileName = getPackageDirectory(packageDefinition) + typeDefinition.getName() + ".java";
      }
      output.put(fileName, text.toString());
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
    }
  }

  private static GrTopStatement[] getTopStatementsInReadAction(final GroovyFileBase file) {
    if (file == null) return new GrTopStatement[0];

    return ApplicationManager.getApplication().runReadAction(new Computable<GrTopStatement[]>() {
      public GrTopStatement[] compute() {
        return file.getTopStatements();
      }
    });
  }

  private void writeTypeDefinition(StringBuilder text,
                                   @NotNull final PsiClass typeDefinition,
                                   @Nullable GrPackageDefinition packageDefinition,
                                   boolean toplevel) {
    final boolean isScript = typeDefinition instanceof GroovyScriptClass;

    writePackageStatement(text, packageDefinition);

    boolean isEnum = typeDefinition.isEnum();
    boolean isAnnotationType = typeDefinition.isAnnotationType();
    boolean isInterface = !isAnnotationType && typeDefinition.isInterface();
    boolean isClassDef = !isInterface && !isEnum && !isAnnotationType && !isScript;

    GenerationUtil.writeClassModifiers(text, typeDefinition.getModifierList(), typeDefinition.isInterface(), toplevel);

    if (isInterface) {
      text.append("interface");
    }
    else if (isEnum) {
      text.append("enum");
    }
    else if (isAnnotationType) {
      text.append("@interface");
    }
    else {
      text.append("class");
    }

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

      final Collection<PsiClassType> implementsTypes = new LinkedHashSet<PsiClassType>();
      Collections.addAll(implementsTypes, typeDefinition.getImplementsListTypes());
      /*for (PsiClass aClass : collectDelegateTypes(typeDefinition)) {
        if (aClass.isInterface()) {
          implementsTypes.add(JavaPsiFacade.getElementFactory(myProject).createType(aClass));
        } else {
          Collections.addAll(implementsTypes, aClass.getImplementsListTypes());
        }
      }*/

      if (!implementsTypes.isEmpty()) {
        text.append(isInterface ? "extends " : "implements ");
        text.append(StringUtil.join(implementsTypes, new Function<PsiClassType, String>() {
            @Override
            public String fun(PsiClassType psiClassType) {
              return getTypeText(psiClassType, typeDefinition, false);
            }
          }, ", "));
        text.append(" ");
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

  private void writeAllMethods(StringBuilder text, Collection<PsiMethod> methods, PsiClass aClass) {
    Set<MethodSignature> methodSignatures = new HashSet<MethodSignature>();
    for (PsiMethod method : methods) {
      if (!shouldBeGenerated(method)) {
        continue;
      }

      if (method instanceof GrConstructor) {
        writeConstructor(text, (GrConstructor)method, aClass.isEnum(), true);
        continue;
      }

      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length > 0) {
        PsiParameter[] parametersCopy = new PsiParameter[parameters.length];
        PsiType[] parameterTypes = new PsiType[parameters.length];
        for (int i = 0; i < parameterTypes.length; i++) {
          parametersCopy[i] = parameters[i];
          parameterTypes[i] = findOutParameterType(parameters[i]);
        }

        for (int i = parameters.length - 1; i >= 0; i--) {
          MethodSignature signature =
            MethodSignatureUtil.createMethodSignature(method.getName(), parameterTypes, method.getTypeParameters(), PsiSubstitutor.EMPTY);
          if (methodSignatures.add(signature)) {
            writeMethod(text, method, parametersCopy, true);
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
          writeMethod(text, method, parameters, true);
        }
      }
    }
  }

  private static boolean shouldBeGenerated(PsiMethod method) {
    for (PsiMethod psiMethod : method.findSuperMethods()) {
      if (!psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
        final PsiType type = method.getReturnType();
        final PsiType superType = psiMethod.getReturnType();
        if (type != null && superType != null && !superType.isAssignableFrom(type)) {
          return false;
        }
      }
    }
    return true;
  }

  private Collection<PsiMethod> collectMethods(PsiClass typeDefinition, boolean classDef) {
    List<PsiMethod> methods = new ArrayList<PsiMethod>();
    ContainerUtil.addAll(methods, typeDefinition.getMethods());
    if (classDef) {
      final Collection<MethodSignature> toOverride = OverrideImplementUtil.getMethodSignaturesToOverride(typeDefinition);
      for (MethodSignature signature : toOverride) {
        if (signature instanceof MethodSignatureBackedByPsiMethod) {
          final PsiMethod method = ((MethodSignatureBackedByPsiMethod)signature).getMethod();
          final PsiClass baseClass = method.getContainingClass();
          if (isAbstractInJava(method) && baseClass != null && typeDefinition.isInheritor(baseClass, true)) {
            methods.add(mirrorMethod(typeDefinition, method, baseClass, PsiSubstitutor.EMPTY, GenerationUtil.JAVA_MODIFIERS));
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

    if (typeDefinition instanceof GrTypeDefinition) {
      for (PsiMethod delegatedMethod : GrClassImplUtil.getDelegatedMethods((GrTypeDefinition)typeDefinition)) {
        methods.add(delegatedMethod);
      }
    }

    return methods;
  }

  private static LightMethodBuilder mirrorMethod(PsiClass typeDefinition,
                                                 PsiMethod method,
                                                 PsiClass baseClass,
                                                 PsiSubstitutor substitutor,
                                                 String... modifierFilter) {
    final LightMethodBuilder builder = new LightMethodBuilder(method.getManager(), method.getName());
    substitutor = substitutor.putAll(TypeConversionUtil.getSuperClassSubstitutor(baseClass, typeDefinition, PsiSubstitutor.EMPTY));
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      builder.addParameter(StringUtil.notNullize(parameter.getName()), substitutor.substitute(findOutParameterType(parameter)));
    }
    builder.setReturnType(substitutor.substitute(method.getReturnType()));
    for (String modifier : modifierFilter) {
      if (method.hasModifierProperty(modifier)) {
        builder.addModifier(modifier);
      }
    }
    return builder;
  }

  private static boolean isAbstractInJava(PsiMethod method) {
    if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return true;
    }

    final PsiClass psiClass = method.getContainingClass();
    return psiClass != null && GrClassSubstitutor.getSubstitutedClass(psiClass).isInterface();
  }

  private void appendTypeParameters(StringBuilder text, PsiTypeParameterListOwner typeParameterListOwner) {
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

  private void writeEnumConstants(StringBuilder text, GrEnumTypeDefinition enumDefinition) {
    text.append("\n  ");
    GrEnumConstant[] enumConstants = enumDefinition.getEnumConstants();
    for (int i = 0; i < enumConstants.length; i++) {
      if (i > 0) text.append(", ");
      GrEnumConstant enumConstant = enumConstants[i];
      text.append(enumConstant.getName());
      PsiMethod constructor = enumConstant.resolveMethod();
      if (constructor != null) {
        text.append("(");
        writeStubConstructorInvocation(text, constructor, PsiSubstitutor.EMPTY);
        text.append(")");
      }

      GrTypeDefinitionBody block = enumConstant.getAnonymousBlock();
      if (block != null) {
        text.append("{\n");
        for (PsiMethod method : block.getMethods()) {
          writeMethod(text, method, method.getParameterList().getParameters(), true);
        }
        text.append("}");
      }
    }
    text.append(";");
  }

  private void writeStubConstructorInvocation(StringBuilder text, PsiMethod constructor, PsiSubstitutor substitutor) {
    final PsiParameter[] superParams = constructor.getParameterList().getParameters();
    for (int j = 0; j < superParams.length; j++) {
      if (j > 0) text.append(", ");
      String typeText = getTypeText(substitutor.substitute(findOutParameterType(superParams[j])), null, false);
      text.append("(").append(typeText).append(")").append(getDefaultValueText(typeText));
    }
  }

  private static void writePackageStatement(StringBuilder text, GrPackageDefinition packageDefinition) {
    if (packageDefinition != null) {
      text.append("package ");
      text.append(packageDefinition.getPackageName());
      text.append(";");
      text.append("\n");
      text.append("\n");
    }
  }

  private void writeConstructor(final StringBuilder text, final GrConstructor constructor, boolean isEnum, final boolean prefix) {
    if (prefix) {
      text.append("\n");
      text.append("  ");
    }
    if (!isEnum) {
      text.append("public ");
      //writeModifiers(text, constructor.getModifierList(), JAVA_MODIFIERS);
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

    final Set<String> result = CollectionFactory.newTroveSet(ArrayUtil.EMPTY_STRING_ARRAY);
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

  private void writeVariableDeclarations(StringBuilder text, GrVariableDeclaration variableDeclaration) {
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
      GenerationUtil.writeModifiers(text, modifierList, GenerationUtil.JAVA_MODIFIERS);

      //type
      text.append(type).append(" ").append(name).append(" = ").append(initializer);
      text.append(";\n");
    }
  }

  public static String generateMethodStub(@NotNull PsiMethod method) {
    if (!(method instanceof GroovyPsiElement)) {
      return method.getText();
    }

    final GroovyToJavaGenerator generator = new GroovyToJavaGenerator(method.getProject(), Collections.<VirtualFile>emptyList(), false);
    final StringBuilder buffer = new StringBuilder();
    if (method instanceof GrConstructor) {
      generator.writeConstructor(buffer, (GrConstructor)method, false, false);
    }
    else {
      generator.writeMethod(buffer, method, method.getParameterList().getParameters(), false);
    }
    return buffer.toString();
  }

  private void writeMethod(StringBuilder text, PsiMethod method, final PsiParameter[] parameters, final boolean prefix) {
    if (method == null) return;
    String name = method.getName();
    if (!JavaPsiFacade.getInstance(method.getProject()).getNameHelper().isIdentifier(name))
      return; //does not have a java image

    boolean isAbstract = isAbstractInJava(method);

    PsiModifierList modifierList = method.getModifierList();

    if (prefix) {
      text.append("\n");
      text.append("  ");
    }
    GenerationUtil.writeModifiers(text, modifierList, GenerationUtil.JAVA_MODIFIERS);
    if (method.hasTypeParameters()) {
      appendTypeParameters(text, method);
      text.append(" ");
    }

    //append return type
    PsiType retType = findOutReturnTypeOfMethod(method);

    if (!method.hasModifierProperty(PsiModifier.STATIC)) {
      final List<MethodSignatureBackedByPsiMethod> superSignatures = method.findSuperMethodSignaturesIncludingStatic(true);
      for (MethodSignatureBackedByPsiMethod superSignature : superSignatures) {
        final PsiType superType = superSignature.getSubstitutor().substitute(superSignature.getMethod().getReturnType());
        if (superType != null && !superType.isAssignableFrom(retType) && !(PsiUtil.resolveClassInType(superType) instanceof PsiTypeParameter)) {
          retType = superType;
        }
      }
    }

    text.append(getTypeText(retType, method, false));
    text.append(" ");

    text.append(name);

    writeParameterList(text, parameters);

    writeThrowsList(text, method);

    if (!isAbstract) {
      /************* body **********/
      generateMethodBody(text, method, retType);
    }
    else {
      text.append(";");
    }
    text.append("\n");
  }

  private void generateMethodBody(StringBuilder text, PsiMethod method, PsiType retType) {
    if (!fullConversion) {
      text.append("{\n    return ");
      text.append(getDefaultValueText(getTypeText(retType, method, false)));
      text.append(";\n  }");
      return;
    }
    //todo
    if (method instanceof GrMethod) {
      final CodeBlockGenerator blockGenerator = new CodeBlockGenerator(new StringBuilder(), myProject);
      //blockGenerator.generate();
      //todo
    }
  }

  private void writeThrowsList(StringBuilder text, PsiMethod method) {
    final PsiReferenceList throwsList = method.getThrowsList();
    final PsiClassType[] exceptions = throwsList.getReferencedTypes();
    if (exceptions.length > 0) {
      text.append("throws ");
      for (int i = 0; i < exceptions.length; i++) {
        PsiClassType exception = exceptions[i];
        if (i != 0) {
          text.append(",");
        }
        text.append(getTypeText(exception, method, false));
        text.append(" ");
      }

      //todo search for all thrown exceptions from this file methods
    }
  }

  private PsiType findOutReturnTypeOfMethod(PsiMethod method) {
    final PsiType returnType = method.getReturnType();
    if (returnType != null) return returnType;

    if (!fullConversion) return TypesUtil.getJavaLangObject(method);

    final PsiType smartReturnType = org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.getSmartReturnType(method);
    if (smartReturnType != null) return smartReturnType;

    //todo make smarter. search for usages and infer type from them
    return TypesUtil.getJavaLangObject(method);
    //final Collection<PsiReference> collection = MethodReferencesSearch.search(method).findAll();
  }

  private void writeParameterList(StringBuilder text, PsiParameter[] parameters) {
    text.append("(");

    //writes myParameters
    int i = 0;
    while (i < parameters.length) {
      PsiParameter parameter = parameters[i];
      if (parameter == null) continue;

      if (i > 0) text.append(", ");  //append ','

      text.append(getTypeText(findOutParameterType(parameter), parameter, i == parameters.length - 1));
      text.append(" ");
      text.append(parameter.getName());

      i++;
    }
    text.append(")");
    text.append(" ");
  }

  private static PsiType findOutParameterType(PsiParameter parameter) {
    return parameter.getType(); //todo make smarter
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

}
