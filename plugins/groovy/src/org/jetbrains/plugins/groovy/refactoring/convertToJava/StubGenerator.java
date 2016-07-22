/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumConstantInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyConstantExpressionEvaluator;
import org.jetbrains.plugins.groovy.lang.resolve.ast.DelegatedMethod;

import java.util.*;

/**
 * @author Maxim.Medvedev
 */
public class StubGenerator implements ClassItemGenerator {
  public static final String[] STUB_MODIFIERS = {
    PsiModifier.PUBLIC,
    PsiModifier.PROTECTED,
    PsiModifier.PRIVATE,
    PsiModifier.PACKAGE_LOCAL,
    PsiModifier.STATIC,
    PsiModifier.ABSTRACT,
    PsiModifier.FINAL,
    PsiModifier.NATIVE,
  };

  private static final String[] STUB_FIELD_MODIFIERS = {
    PsiModifier.PUBLIC,
    PsiModifier.PROTECTED,
    PsiModifier.PRIVATE,
    PsiModifier.PACKAGE_LOCAL,
    PsiModifier.STATIC,
    PsiModifier.FINAL,
  };

  private final ClassNameProvider classNameProvider;
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.convertToJava.StubGenerator");

  public StubGenerator(ClassNameProvider classNameProvider) {
    this.classNameProvider = classNameProvider;
  }

  @Override
  public void writeEnumConstant(StringBuilder text, GrEnumConstant enumConstant) {
    text.append(enumConstant.getName());
    PsiMethod constructor = enumConstant.resolveMethod();
    if (constructor != null) {
      text.append('(');
      writeStubConstructorInvocation(text, constructor, PsiSubstitutor.EMPTY, enumConstant);
      text.append(')');
    }

    GrEnumConstantInitializer initializer = enumConstant.getInitializingClass();
    if (initializer != null) {
      text.append("{\n");
      for (PsiMethod method : initializer.getMethods()) {
        writeMethod(text, method);
      }
      text.append('}');
    }
  }


  private void writeStubConstructorInvocation(StringBuilder text,
                                              PsiMethod constructor,
                                              PsiSubstitutor substitutor,
                                              PsiElement invocation) {
    final PsiParameter[] superParams = constructor.getParameterList().getParameters();
    for (int j = 0; j < superParams.length; j++) {
      if (j > 0) text.append(", ");
      text.append('(');
      final PsiType type = superParams[j].getType();
      TypeWriter.writeType(text, substitutor.substitute(type), invocation, classNameProvider);
      text.append(')').append(GroovyToJavaGenerator.getDefaultValueText(type.getCanonicalText()));
    }
  }


  @Override
  public void writeConstructor(final StringBuilder text, PsiMethod constructor, boolean isEnum) {
    LOG.assertTrue(constructor.isConstructor());

    if (!isEnum) {
      text.append("public ");
      //writeModifiers(text, constructor.getModifierList(), JAVA_MODIFIERS);
    }

    /************* name **********/
    //append constructor name
    text.append(constructor.getName());

    /************* parameters **********/
    GenerationUtil.writeParameterList(text, constructor.getParameterList().getParameters(), classNameProvider, null);

    final Set<String> throwsTypes = collectThrowsTypes(constructor, new THashSet<>());
    if (!throwsTypes.isEmpty()) {
      text.append("throws ").append(StringUtil.join(throwsTypes, ", ")).append(' ');
    }

    /************* body **********/

    text.append("{\n");
    if (constructor instanceof GrReflectedMethod) {
      constructor = ((GrReflectedMethod)constructor).getBaseMethod();
    }
    if (constructor instanceof GrMethod) {
      final GrConstructorInvocation invocation = PsiImplUtil.getChainingConstructorInvocation((GrMethod)constructor);
      if (invocation != null) {
        final GroovyResolveResult resolveResult = resolveChainingConstructor((GrMethod)constructor);
        if (resolveResult != null) {
          text.append(invocation.isSuperCall() ? "super(" : "this(");
          writeStubConstructorInvocation(text, (PsiMethod)resolveResult.getElement(), resolveResult.getSubstitutor(), invocation);
          text.append(");");
        }
      }
      else if (constructor instanceof LightElement) {
        writeStubConstructorInvocation(constructor, text);
      }
    }

    text.append("\n}\n");
  }

  private void writeStubConstructorInvocation(PsiMethod constructor, StringBuilder text) {
    final PsiClass containingClass = constructor.getContainingClass();
    if (containingClass == null) return;

    final PsiClass superClass = containingClass.getSuperClass();
    if (superClass == null) return;

    final PsiMethod[] constructors = superClass.getConstructors();
    if (constructors.length == 0) return;

    for (PsiMethod method : constructors) {
      if (method.getParameterList().getParameters().length == 0 && PsiUtil.isAccessible(method, containingClass, containingClass)) {
        return; //default constructor exists
      }
    }

    for (PsiMethod method : constructors) {
      if (PsiUtil.isAccessible(method, containingClass, containingClass)) {
        text.append("super(");
        writeStubConstructorInvocation(text, method, TypeConversionUtil.getSuperClassSubstitutor(superClass, containingClass, PsiSubstitutor.EMPTY), constructor);
        text.append(");");
        return;
      }
    }
  }

  private Set<String> collectThrowsTypes(PsiMethod constructor, Set<PsiMethod> visited) {
    LOG.assertTrue(constructor.isConstructor());

    final GroovyResolveResult resolveResult = constructor instanceof GrMethod ? resolveChainingConstructor((GrMethod)constructor) : null;
    
    if (resolveResult == null) {
      return Collections.emptySet();
    }


    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    final PsiMethod chainedConstructor = (PsiMethod)resolveResult.getElement();
    assert chainedConstructor != null;

    if (!visited.add(chainedConstructor)) {
      return Collections.emptySet();
    }

    final Set<String> result = ContainerUtil.newTroveSet(ArrayUtil.EMPTY_STRING_ARRAY);
    for (PsiClassType type : chainedConstructor.getThrowsList().getReferencedTypes()) {
      StringBuilder builder = new StringBuilder();
      TypeWriter.writeType(builder, substitutor.substitute(type), constructor, classNameProvider);
      result.add(builder.toString());
    }

    if (chainedConstructor instanceof GrMethod) {
      LOG.assertTrue(chainedConstructor.isConstructor());
      result.addAll(collectThrowsTypes(chainedConstructor, visited));
    }
    return result;
  }

  @Override
  public void writeMethod(StringBuilder text, PsiMethod method) {
    if (method == null) return;
    String name = method.getName();
    if (!PsiNameHelper.getInstance(method.getProject()).isIdentifier(name)) {
      return; //does not have a java image
    }

    boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);

    PsiModifierList modifierList = method.getModifierList();

    ModifierListGenerator.writeModifiers(text, modifierList, STUB_MODIFIERS, false);
    if (method.hasTypeParameters()) {
      GenerationUtil.writeTypeParameters(text, method, classNameProvider);
      text.append(' ');
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

    TypeWriter.writeType(text, retType, method, classNameProvider);
    text.append(' ');

    text.append(name);


    GenerationUtil.writeParameterList(text, method.getParameterList().getParameters(), classNameProvider, null);

    writeThrowsList(text, method);

    if (!isAbstract && !method.hasModifierProperty(PsiModifier.NATIVE)) {
      /************* body **********/
      text.append("{\nreturn ");
      text.append(GroovyToJavaGenerator.getDefaultValueText(retType.getCanonicalText()));
      text.append(";\n}");
    }
    else {
      text.append(';');
    }
    text.append('\n');
  }

  private void writeThrowsList(StringBuilder text, PsiMethod method) {
    final PsiReferenceList throwsList = method.getThrowsList();
    final PsiClassType[] exceptions = throwsList.getReferencedTypes();
    GenerationUtil.writeThrowsList(text, throwsList, exceptions, classNameProvider);
  }

  @Nullable
  private static GroovyResolveResult resolveChainingConstructor(GrMethod constructor) {
    LOG.assertTrue(constructor.isConstructor());

    final GrConstructorInvocation constructorInvocation = PsiImplUtil.getChainingConstructorInvocation(constructor);
    if (constructorInvocation == null) {
      return null;
    }

    GroovyResolveResult resolveResult = constructorInvocation.advancedResolve();
    if (resolveResult.getElement() != null) {
      return resolveResult;
    }

    final GroovyResolveResult[] results = constructorInvocation.multiResolve(false);
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

  @Override
  public Collection<PsiMethod> collectMethods(PsiClass typeDefinition) {
    List<PsiMethod> methods = new ArrayList<>();
    for (PsiMethod method : typeDefinition.getMethods()) {
      if (method instanceof DelegatedMethod) {
        PsiMethod prototype = ((DelegatedMethod)method).getPrototype();
        PsiClass aClass = prototype.getContainingClass();
        if (prototype.hasModifierProperty(PsiModifier.FINAL) && aClass != null && typeDefinition.isInheritor(aClass, true)) {
          continue; //skip final super methods
        }
      }
      methods.add(method);
    }
    boolean isClass = !typeDefinition.isInterface() &&
                      !typeDefinition.isAnnotationType() &&
                      !typeDefinition.isEnum() &&
                      !(typeDefinition instanceof GroovyScriptClass);
    if (isClass) {
      final Collection<MethodSignature> toOverride = OverrideImplementExploreUtil.getMethodSignaturesToOverride(typeDefinition);
      for (MethodSignature signature : toOverride) {
        if (!(signature instanceof MethodSignatureBackedByPsiMethod)) continue;

        final PsiMethod method = ((MethodSignatureBackedByPsiMethod)signature).getMethod();
        final PsiClass baseClass = method.getContainingClass();
        if (baseClass == null) continue;
        final String qname = baseClass.getQualifiedName();
        if (GroovyCommonClassNames.DEFAULT_BASE_CLASS_NAME.equals(qname) || GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT.equals(qname) ||
            method.hasModifierProperty(PsiModifier.ABSTRACT) && typeDefinition.isInheritor(baseClass, true)) {
          if (method.isConstructor()) continue;
          methods.add(mirrorMethod(typeDefinition, method, baseClass, signature.getSubstitutor()));
        }
      }

      final Collection<MethodSignature> toImplement = OverrideImplementExploreUtil.getMethodSignaturesToImplement(typeDefinition);
      for (MethodSignature signature : toImplement) {
        if (!(signature instanceof MethodSignatureBackedByPsiMethod)) continue;
        final PsiMethod resolved = ((MethodSignatureBackedByPsiMethod)signature).getMethod();
        final PsiClass baseClass = resolved.getContainingClass();
        if (baseClass == null) continue;
        if (!GroovyCommonClassNames.DEFAULT_BASE_CLASS_NAME.equals(baseClass.getQualifiedName())) continue;

        methods.add(mirrorMethod(typeDefinition, resolved, baseClass, signature.getSubstitutor()));
      }
    }

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
    builder.setMethodReturnType(substitutor.substitute(method.getReturnType()));
    for (String modifier : STUB_MODIFIERS) {
      if (method.hasModifierProperty(modifier)) {
        builder.addModifier(modifier);
      }
    }
    return builder;
  }

  @Override
  public void writeVariableDeclarations(StringBuilder text, GrVariableDeclaration variableDeclaration) {
    GrTypeElement typeElement = variableDeclaration.getTypeElementGroovy();

    final GrModifierList modifierList = variableDeclaration.getModifierList();
    final PsiNameHelper nameHelper = PsiNameHelper.getInstance(variableDeclaration.getProject());
    for (final GrVariable variable : variableDeclaration.getVariables()) {
      String name = variable.getName();
      if (!nameHelper.isIdentifier(name)) {
        continue; //does not have a java image
      }

      ModifierListGenerator.writeModifiers(text, modifierList, STUB_FIELD_MODIFIERS, false);

      //type
      PsiType declaredType =
        typeElement == null ? PsiType.getJavaLangObject(variable.getManager(), variable.getResolveScope()) : typeElement.getType();

      TypeWriter.writeType(text, declaredType, variableDeclaration, classNameProvider);
      text.append(' ').append(name).append(" = ").append(getVariableInitializer(variable, declaredType));
      text.append(";\n");
    }
  }

  private static String getVariableInitializer(GrVariable variable, PsiType declaredType) {
    if (declaredType instanceof PsiPrimitiveType) {
      Object eval = GroovyConstantExpressionEvaluator.evaluate(variable.getInitializerGroovy());
      if (eval instanceof Float ||
          PsiType.FLOAT.equals(TypesUtil.unboxPrimitiveTypeWrapper(variable.getType())) && eval instanceof Number) {
        return eval.toString() + "f";
      }
      else if (eval instanceof Character) {
        StringBuilder buffer = new StringBuilder();
        buffer.append('\'');
        StringUtil.escapeStringCharacters(1, Character.toString(((Character)eval).charValue()), buffer);
        buffer.append('\'');
        return buffer.toString();
      }
      if (eval instanceof Number || eval instanceof Boolean) {
        return eval.toString();
      }
    }
    return GroovyToJavaGenerator.getDefaultValueText(declaredType.getCanonicalText());
  }

  @Override
  public void writeImplementsList(StringBuilder text, PsiClass typeDefinition) {
    final Collection<PsiClassType> implementsTypes = new LinkedHashSet<>();
    Collections.addAll(implementsTypes, typeDefinition.getImplementsListTypes());

    if (implementsTypes.isEmpty()) return;

    text.append(typeDefinition.isInterface() ? "extends " : "implements ");
    for (PsiClassType implementsType : implementsTypes) {
      TypeWriter.writeType(text, implementsType, typeDefinition, classNameProvider);
      text.append(", ");
    }
    if (!implementsTypes.isEmpty()) text.delete(text.length() - 2, text.length());
    text.append(' ');
  }

  @Override
  public void writeExtendsList(StringBuilder text, PsiClass typeDefinition) {
    final PsiClassType[] extendsClassesTypes = typeDefinition.getExtendsListTypes();

    if (extendsClassesTypes.length > 0) {

      text.append("extends ");
      TypeWriter.writeType(text, extendsClassesTypes[0], typeDefinition, classNameProvider);
      text.append(' ');
    }
  }
}
