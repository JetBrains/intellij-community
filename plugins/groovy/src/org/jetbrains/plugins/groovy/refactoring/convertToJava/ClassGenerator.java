/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrClassSubstitutor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrConstructor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil;

import java.util.*;

import static org.jetbrains.plugins.groovy.refactoring.convertToJava.GenerationUtil.writeType;

/**
 * @author Maxim.Medvedev
 */
public class ClassGenerator {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.convertToJava.ClassGenerator");

  private Project myProject;
  private ClassNameProvider classNameProvider;

  public ClassGenerator(Project project, ClassNameProvider classNameProvider) {
    myProject = project;
    this.classNameProvider = classNameProvider;
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

  public void writeTypeDefinition(StringBuilder text, @NotNull final PsiClass typeDefinition, boolean toplevel) {
    final boolean isScript = typeDefinition instanceof GroovyScriptClass;

    final GroovyFile containingFile = (GroovyFile)typeDefinition.getContainingFile();
    writePackageStatement(text, containingFile.getPackageDefinition());

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

        text.append("extends ");
        writeType(text, extendsClassesTypes[0], typeDefinition, classNameProvider);
        text.append(" ");
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
        for (PsiClassType implementsType : implementsTypes) {
          writeType(text, implementsType, typeDefinition, classNameProvider);
          text.append(", ");
        }
        if (implementsTypes.size() > 0) text.delete(text.length() - 2, text.length());
        text.append(" ");
      }
    }

    text.append("{\n");

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
      for (PsiClass inner : typeDefinition.getInnerClasses()) {
        writeTypeDefinition(text, inner, false);
        text.append("\n");
      }
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
        writeConstructor(text, (GrConstructor)method, aClass.isEnum());
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
            writeMethod(text, method, parametersCopy);
            text.append('\n');

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
          text.append('\n');
        }
      }
    }
  }

  private void appendTypeParameters(StringBuilder text, PsiTypeParameterListOwner typeParameterListOwner) {
    if (!typeParameterListOwner.hasTypeParameters()) return;

    text.append("<");
    PsiTypeParameter[] parameters = typeParameterListOwner.getTypeParameters();
    final PsiTypeParameterList typeParameterList = typeParameterListOwner.getTypeParameterList();
    for (int i = 0; i < parameters.length; i++) {
      if (i > 0) text.append(", ");
      PsiTypeParameter parameter = parameters[i];
      text.append(parameter.getName());
      PsiClassType[] extendsListTypes = parameter.getExtendsListTypes();
      if (extendsListTypes.length > 0) {
        text.append(" extends ");
        for (int j = 0; j < extendsListTypes.length; j++) {
          if (j > 0) text.append(" & ");
          writeType(text, extendsListTypes[j], typeParameterList, classNameProvider);
        }
      }
    }
    text.append(">");
  }

  private void writeParameterList(StringBuilder text, PsiParameter[] parameters) {
    text.append("(");

    //writes myParameters
    int i = 0;
    while (i < parameters.length) {
      PsiParameter parameter = parameters[i];
      if (parameter == null) continue;

      if (i > 0) text.append(", ");  //append ','
      writeType(text, findOutParameterType(parameter), parameter, classNameProvider);
      text.append(" ");
      text.append(parameter.getName());

      i++;
    }
    text.append(")");
    text.append(" ");
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
        writeType(text, exception, throwsList, classNameProvider);
        text.append(" ");
      }

      //todo search for all thrown exceptions from this file methods
    }
  }

  private void writeMethodBody(StringBuilder text, PsiMethod method, PsiType retType) {
    if (classNameProvider.forStubs()) {
      text.append("{\nreturn ");
      text.append(GroovyToJavaGenerator.getDefaultValueText(retType.getCanonicalText()));
      text.append(";\n}");
      return;
    }
    if (method instanceof GrMethod) {
      final CodeBlockGenerator blockGenerator = new CodeBlockGenerator(text, myProject);
      final GrOpenBlock block = ((GrMethod)method).getBlock();
      LOG.assertTrue(block != null);
      block.accept(blockGenerator);
    }
  }

  private PsiType findOutReturnTypeOfMethod(PsiMethod method) {
    final PsiType returnType = method.getReturnType();
    if (returnType != null) return returnType;

    if (classNameProvider.forStubs()) return TypesUtil.getJavaLangObject(method);

    final PsiType smartReturnType = org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.getSmartReturnType(method);
    if (smartReturnType != null) return smartReturnType;

    //todo make smarter. search for usages and infer type from them
    return TypesUtil.getJavaLangObject(method);
    //final Collection<PsiReference> collection = MethodReferencesSearch.search(method).findAll();
  }

  private static PsiType findOutParameterType(PsiParameter parameter) {
    return parameter.getType(); //todo make smarter
  }


  void writeMethod(StringBuilder text, PsiMethod method, final PsiParameter[] parameters) {
    if (method == null) return;
    String name = method.getName();
    if (!JavaPsiFacade.getInstance(method.getProject()).getNameHelper().isIdentifier(name)) {
      return; //does not have a java image
    }

    boolean isAbstract = isAbstractInJava(method);

    PsiModifierList modifierList = method.getModifierList();

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
        if (superType != null &&
            !superType.isAssignableFrom(retType) &&
            !(PsiUtil.resolveClassInType(superType) instanceof PsiTypeParameter)) {
          retType = superType;
        }
      }
    }

    writeType(text, retType, method, classNameProvider);
    text.append(" ");

    text.append(name);

    writeParameterList(text, parameters);

    writeThrowsList(text, method);

    if (!isAbstract) {
      /************* body **********/
      writeMethodBody(text, method, retType);
    }
    else {
      text.append(";");
    }
    text.append("\n");
  }


  private void writeVariableDeclarations(StringBuilder text, GrVariableDeclaration variableDeclaration) {
    GrTypeElement typeElement = variableDeclaration.getTypeElementGroovy();

    final GrModifierList modifierList = variableDeclaration.getModifierList();
    final PsiNameHelper nameHelper = JavaPsiFacade.getInstance(variableDeclaration.getProject()).getNameHelper();
    for (final GrVariable variable : variableDeclaration.getVariables()) {
      String name = variable.getName();
      if (!nameHelper.isIdentifier(name)) {
        continue; //does not have a java image
      }

      GenerationUtil.writeModifiers(text, modifierList, GenerationUtil.JAVA_MODIFIERS);

      //type
      PsiType declaredType =
        typeElement == null ? PsiType.getJavaLangObject(variable.getManager(), variable.getResolveScope()) : typeElement.getType();
       final String initializer = GroovyToJavaGenerator.getDefaultValueText(declaredType.getCanonicalText());

      writeType(text, declaredType, variableDeclaration, classNameProvider);
      text.append(" ").append(name).append(" = ").append(initializer);
      text.append(";\n");
    }
  }


  private void writeEnumConstants(StringBuilder text, GrEnumTypeDefinition enumDefinition) {
    GrEnumConstant[] enumConstants = enumDefinition.getEnumConstants();
    for (int i = 0; i < enumConstants.length; i++) {
      if (i > 0) text.append(", ");
      GrEnumConstant enumConstant = enumConstants[i];
      text.append(enumConstant.getName());
      PsiMethod constructor = enumConstant.resolveMethod();
      if (constructor != null) {
        text.append("(");
        writeStubConstructorInvocation(text, constructor, PsiSubstitutor.EMPTY, enumConstant);
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
    text.append(";\n");
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
    if (classDef && classNameProvider.forStubs()) {
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

  void writeConstructor(final StringBuilder text, final GrConstructor constructor, boolean isEnum) {
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
        text.append(invocation.isSuperCall() ? "super(" : "this(");
        writeStubConstructorInvocation(text, (PsiMethod)resolveResult.getElement(), resolveResult.getSubstitutor(), invocation);
        text.append(");");
      }
    }

    text.append("\n}\n");
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
      StringBuilder builder = new StringBuilder();
      writeType(builder, substitutor.substitute(type), constructor, classNameProvider);
      result.add(builder.toString());
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
      while (results.length > i + 1) {
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

  private void writeStubConstructorInvocation(StringBuilder text, PsiMethod constructor, PsiSubstitutor substitutor, PsiElement invocation) {
    final PsiParameter[] superParams = constructor.getParameterList().getParameters();
    for (int j = 0; j < superParams.length; j++) {
      if (j > 0) text.append(", ");
      text.append("(");
      final PsiType type = findOutParameterType(superParams[j]);
      writeType(text, substitutor.substitute(type), invocation,classNameProvider);
      text.append(")").append(GroovyToJavaGenerator.getDefaultValueText(type.getCanonicalText()));
    }
  }
}
