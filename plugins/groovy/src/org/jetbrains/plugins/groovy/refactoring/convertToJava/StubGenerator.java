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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumConstantInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrConstructor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.*;

import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.DEFAULT_BASE_CLASS_NAME;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT;
import static org.jetbrains.plugins.groovy.refactoring.convertToJava.GenerationUtil.writeType;

/**
 * @author Maxim.Medvedev
 */
public class StubGenerator implements ClassItemGenerator {
  public static final String[] STUB_MODIFIERS = new String[]{
    PsiModifier.PUBLIC,
    PsiModifier.PROTECTED,
    PsiModifier.PRIVATE,
    PsiModifier.PACKAGE_LOCAL,
    PsiModifier.STATIC,
    PsiModifier.ABSTRACT,
    PsiModifier.FINAL,
    PsiModifier.NATIVE,
  };

  private ClassNameProvider classNameProvider;
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.convertToJava.StubGenerator");

  public StubGenerator(ClassNameProvider classNameProvider) {
    this.classNameProvider = classNameProvider;
  }

  public void writeEnumConstant(StringBuilder text, GrEnumConstant enumConstant) {
    text.append(enumConstant.getName());
    PsiMethod constructor = enumConstant.resolveMethod();
    if (constructor != null) {
      text.append("(");
      writeStubConstructorInvocation(text, constructor, PsiSubstitutor.EMPTY, enumConstant);
      text.append(")");
    }

    GrEnumConstantInitializer initializer = enumConstant.getConstantInitializer();
    if (initializer != null) {
      text.append("{\n");
      for (PsiMethod method : initializer.getMethods()) {
        writeMethod(text, method, 0);
      }
      text.append("}");
    }
  }


  private void writeStubConstructorInvocation(StringBuilder text,
                                              PsiMethod constructor,
                                              PsiSubstitutor substitutor,
                                              PsiElement invocation) {
    final PsiParameter[] superParams = constructor.getParameterList().getParameters();
    for (int j = 0; j < superParams.length; j++) {
      if (j > 0) text.append(", ");
      text.append("(");
      final PsiType type = superParams[j].getType();
      writeType(text, substitutor.substitute(type), invocation, classNameProvider);
      text.append(")").append(GroovyToJavaGenerator.getDefaultValueText(type.getCanonicalText()));
    }
  }


  public void writeConstructor(final StringBuilder text, final GrConstructor constructor, int skipOptional, boolean isEnum) {
    if (!isEnum) {
      text.append("public ");
      //writeModifiers(text, constructor.getModifierList(), JAVA_MODIFIERS);
    }

    /************* name **********/
    //append constructor name
    text.append(constructor.getName());

    /************* parameters **********/
    final ArrayList<GrParameter> actual = GenerationUtil.getActualParams(constructor, skipOptional);
    GenerationUtil.writeParameterList(text, actual.toArray(new GrParameter[actual.size()]), classNameProvider, null);

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

  public void writeMethod(StringBuilder text, PsiMethod method, final int skipOptional) {
    if (method == null) return;
    String name = method.getName();
    if (!JavaPsiFacade.getInstance(method.getProject()).getNameHelper().isIdentifier(name)) {
      return; //does not have a java image
    }

    boolean isAbstract = GenerationUtil.isAbstractInJava(method);

    PsiModifierList modifierList = method.getModifierList();

    ModifierListGenerator.writeModifiers(text, modifierList, STUB_MODIFIERS, false);
    if (method.hasTypeParameters()) {
      GenerationUtil.writeTypeParameters(text, method, classNameProvider);
      text.append(" ");
    }

    //append return type
    PsiType retType = method.getReturnType();
    if (retType == null) {
      retType = TypesUtil.getJavaLangObject(method);
    }

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


    if (method instanceof GrMethod) {
      final ArrayList<GrParameter> actualParams = GenerationUtil.getActualParams(((GrMethod)method), skipOptional);
      GenerationUtil.writeParameterList(text, actualParams.toArray(new GrParameter[actualParams.size()]), classNameProvider, null);
    }
    else {
      LOG.assertTrue(skipOptional==0);
      GenerationUtil.writeParameterList(text, method.getParameterList().getParameters(), classNameProvider, null);
    }

    writeThrowsList(text, method);

    if (!isAbstract) {
      /************* body **********/
      text.append("{\nreturn ");
      text.append(GroovyToJavaGenerator.getDefaultValueText(retType.getCanonicalText()));
      text.append(";\n}");
    }
    else {
      text.append(";");
    }
    text.append("\n");
  }

  private void writeThrowsList(StringBuilder text, PsiMethod method) {
    final PsiReferenceList throwsList = method.getThrowsList();
    final PsiClassType[] exceptions = throwsList.getReferencedTypes();
    GenerationUtil.writeThrowsList(text, throwsList, exceptions, classNameProvider);
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

  public Collection<PsiMethod> collectMethods(PsiClass typeDefinition, boolean classDef) {
    List<PsiMethod> methods = new ArrayList<PsiMethod>();
    ContainerUtil.addAll(methods, typeDefinition.getMethods());
    if (classDef) {
      final Collection<MethodSignature> toOverride = OverrideImplementUtil.getMethodSignaturesToOverride(typeDefinition);
      for (MethodSignature signature : toOverride) {
        if (!(signature instanceof MethodSignatureBackedByPsiMethod)) continue;

        final PsiMethod method = ((MethodSignatureBackedByPsiMethod)signature).getMethod();
        final PsiClass baseClass = method.getContainingClass();
        if (baseClass == null) continue;
        final String qname = baseClass.getQualifiedName();
        if (DEFAULT_BASE_CLASS_NAME.equals(qname) || GROOVY_OBJECT_SUPPORT.equals(qname) ||
            GenerationUtil.isAbstractInJava(method) && typeDefinition.isInheritor(baseClass, true)) {
          if (method.isConstructor()) continue;
          methods.add(mirrorMethod(typeDefinition, method, baseClass, signature.getSubstitutor()));
        }
      }

      final Collection<MethodSignature> toImplement = OverrideImplementUtil.getMethodSignaturesToImplement(typeDefinition);
      for (MethodSignature signature : toImplement) {
        if (!(signature instanceof MethodSignatureBackedByPsiMethod)) continue;
        final PsiMethod resolved = ((MethodSignatureBackedByPsiMethod)signature).getMethod();
        final PsiClass baseClass = resolved.getContainingClass();
        if (baseClass == null) continue;
        if (!DEFAULT_BASE_CLASS_NAME.equals(baseClass.getQualifiedName())) continue;

        methods.add(mirrorMethod(typeDefinition, resolved, baseClass, signature.getSubstitutor()));
      }
      /*final PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();
      methods.add(factory.createMethodFromText("public groovy.lang.MetaClass getMetaClass() {}", null));
      methods.add(factory.createMethodFromText("public void setMetaClass(groovy.lang.MetaClass mc) {}", null));
      methods.add(factory.createMethodFromText("public Object invokeMethod(String name, Object args) {}", null));
      methods.add(factory.createMethodFromText("public Object getProperty(String propertyName) {}", null));
      methods.add(factory.createMethodFromText("public void setProperty(String propertyName, Object newValue) {}", null));*/
    }

    /*if (typeDefinition instanceof GrTypeDefinition) {
      for (PsiMethod delegatedMethod : GrClassImplUtil.getDelegatedMethods((GrTypeDefinition)typeDefinition)) {
        methods.add(delegatedMethod);
      }
    }*/

    return methods;
  }

  @Override
  public boolean generateAnnotations() {
    return false;
  }

  @Override
  public void writePostponed(StringBuilder text, PsiClass psiClass) {
  }

  private static LightMethodBuilder mirrorMethod(PsiClass typeDefinition,
                                                 PsiMethod method,
                                                 PsiClass baseClass,
                                                 PsiSubstitutor substitutor) {
    final LightMethodBuilder builder = new LightMethodBuilder(method.getManager(), method.getName());
    substitutor = substitutor.putAll(TypeConversionUtil.getSuperClassSubstitutor(baseClass, typeDefinition, PsiSubstitutor.EMPTY));
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      builder.addParameter(StringUtil.notNullize(parameter.getName()), substitutor.substitute(parameter.getType()));
    }
    builder.setReturnType(substitutor.substitute(method.getReturnType()));
    for (String modifier : STUB_MODIFIERS) {
      if (method.hasModifierProperty(modifier)) {
        builder.addModifier(modifier);
      }
    }
    return builder;
  }

  public void writeVariableDeclarations(StringBuilder text, GrVariableDeclaration variableDeclaration) {
    GrTypeElement typeElement = variableDeclaration.getTypeElementGroovy();

    final GrModifierList modifierList = variableDeclaration.getModifierList();
    final PsiNameHelper nameHelper = JavaPsiFacade.getInstance(variableDeclaration.getProject()).getNameHelper();
    for (final GrVariable variable : variableDeclaration.getVariables()) {
      String name = variable.getName();
      if (!nameHelper.isIdentifier(name)) {
        continue; //does not have a java image
      }

      ModifierListGenerator.writeModifiers(text, modifierList, STUB_MODIFIERS, false);

      //type
      PsiType declaredType =
        typeElement == null ? PsiType.getJavaLangObject(variable.getManager(), variable.getResolveScope()) : typeElement.getType();
       final String initializer = GroovyToJavaGenerator.getDefaultValueText(declaredType.getCanonicalText());

      writeType(text, declaredType, variableDeclaration, classNameProvider);
      text.append(" ").append(name).append(" = ").append(initializer);
      text.append(";\n");
    }
  }

}
