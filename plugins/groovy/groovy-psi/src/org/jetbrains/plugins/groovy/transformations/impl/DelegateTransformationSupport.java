// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.impl;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightParameter;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.impl.source.tree.java.PsiCompositeModifierList;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.ast.DelegatedMethod;
import org.jetbrains.plugins.groovy.lang.resolve.processors.GrScopeProcessorWithHints;
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;

import java.util.*;

public class DelegateTransformationSupport implements AstTransformationSupport {
  @Override
  public void applyTransformation(@NotNull TransformationContext context) {
    Map<PsiType, PsiAnnotation> declaredTypes = new LinkedHashMap<>();
    GrTypeDefinition codeClass = context.getCodeClass();
    for (GrField field : context.getFields()) {
      final PsiAnnotation annotation = PsiImplUtil.getAnnotation(field, GroovyCommonClassNames.GROOVY_LANG_DELEGATE);
      if (annotation == null) continue;
      declaredTypes.putIfAbsent(field.getDeclaredType(), annotation);
    }
    for (PsiMethod method : context.getMethods()) {
      final PsiAnnotation annotation = PsiImplUtil.getAnnotation(method, GroovyCommonClassNames.GROOVY_LANG_DELEGATE);
      if (annotation == null) continue;
      if (!method.getParameterList().isEmpty()) continue;
      declaredTypes.putIfAbsent(method.getReturnType(), annotation);
    }

    declaredTypes.forEach((type, annotation) -> {
      if (!(type instanceof PsiClassType)) return;

      final PsiClassType.ClassResolveResult delegateResult = ((PsiClassType)type).resolveGenerics();
      final PsiClass delegate = delegateResult.getElement();
      if (delegate == null) return;

      DelegateProcessor processor = new DelegateProcessor(context, delegate, annotation);
      delegate.processDeclarations(
        processor,
        ResolveState.initial().put(PsiSubstitutor.KEY, delegateResult.getSubstitutor()),
        null,
        codeClass
      );

      if (!processor.myInterfaces) return;

      Set<PsiClass> visited = new HashSet<>();
      Queue<Pair<PsiClass, PsiSubstitutor>> queue = ContainerUtil.newLinkedList(Pair.create(delegate, delegateResult.getSubstitutor()));

      while (!queue.isEmpty()) {
        Pair<PsiClass, PsiSubstitutor> pair = queue.poll();
        PsiClass currentClass = pair.first;
        PsiSubstitutor substitutor = pair.second;
        if (visited.add(currentClass) && currentClass.isInterface()) {
          context.addInterface(new PsiImmediateClassType(currentClass, substitutor));
          continue;
        }

        for (PsiClassType superType : currentClass.getSuperTypes()) {
          PsiClassType.ClassResolveResult resolveResult = superType.resolveGenerics();
          PsiClass superClass = resolveResult.getElement();
          if (superClass != null) {
            queue.offer(Pair.create(superClass, TypeConversionUtil.getSuperClassSubstitutor(superClass, currentClass, substitutor)));
          }
        }
      }
    });
  }

  private static final class DelegateProcessor extends GrScopeProcessorWithHints {

    private final TransformationContext myContext;
    private final boolean myInterfaces;
    private final boolean myDeprecated;
    private final boolean myKeepMethodAnnotations;
    private final boolean myKeepParameterAnnotations;
    private final Condition<PsiMethod> myIgnoreCondition;

    private DelegateProcessor(@NotNull TransformationContext context, @NotNull PsiClass delegate, @NotNull PsiAnnotation annotation) {
      super(null, EnumSet.of(DeclarationKind.METHOD));
      myContext = context;
      myInterfaces = GrAnnotationUtil.inferBooleanAttributeNotNull(annotation, "interfaces");
      myDeprecated = myInterfaces && delegate.isInterface() || GrAnnotationUtil.inferBooleanAttributeNotNull(annotation, "deprecated");
      myKeepMethodAnnotations = GrAnnotationUtil.inferBooleanAttributeNotNull(annotation, "methodAnnotations");
      myKeepParameterAnnotations = GrAnnotationUtil.inferBooleanAttributeNotNull(annotation, "parameterAnnotations");
      myIgnoreCondition = buildCondition(annotation);
    }

    @Override
    public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
      if (!(element instanceof PsiMethod)) return true;

      PsiMethod method = (PsiMethod)element;
      if (!myIgnoreCondition.value(method)) return true;

      PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
      if (substitutor == null) substitutor = PsiSubstitutor.EMPTY;

      myContext.addMethod(createDelegationMethod(method, substitutor));
      return true;
    }

    @NotNull
    protected PsiMethod createDelegationMethod(@NotNull PsiMethod method, @NotNull PsiSubstitutor substitutor) {
      final LightMethodBuilder builder = new LightMethodBuilder(myContext.getManager(), GroovyLanguage.INSTANCE, method.getName());
      builder.setMethodReturnType(substitutor.substitute(method.getReturnType()));
      builder.setContainingClass(myContext.getCodeClass());
      builder.setNavigationElement(method);
      builder.addModifier(PsiModifier.PUBLIC);

      final PsiTypeParameter[] typeParameters = method.getTypeParameters();

      final PsiClass containingClass = method.getContainingClass();
      boolean isRaw = containingClass != null && PsiUtil.isRawSubstitutor(containingClass, substitutor);
      if (isRaw) {
        substitutor =
          JavaPsiFacade.getInstance(method.getProject()).getElementFactory().createRawSubstitutor(substitutor, typeParameters);
      }

      if (!isRaw) {
        for (PsiTypeParameter typeParameter : typeParameters) {
          builder.addTypeParameter(typeParameter);
        }
      }

      final PsiParameter[] originalParameters = method.getParameterList().getParameters();

      for (int i = 0; i < originalParameters.length; i++) {
        PsiParameter originalParameter = originalParameters[i];
        PsiType type;
        if (isRaw) {
          type = TypeConversionUtil.erasure(substitutor.substitute(originalParameter.getType()));
        }
        else {
          type = substitutor.substitute(originalParameter.getType());
        }
        if (type == null) {
          type = TypesUtil.getJavaLangObject(myContext.getCodeClass());
        }
        final LightParameter lightParameter =
          new LightParameter(StringUtil.notNullize(originalParameter.getName(), "p" + i), type, builder, JavaLanguage.INSTANCE);
        if (myKeepParameterAnnotations) {
          final PsiCompositeModifierList delegatingModifierList = new PsiCompositeModifierList(
            method.getManager(), Collections.singletonList(originalParameter.getModifierList())
          );
          lightParameter.setModifierList(delegatingModifierList);
        }
        builder.addParameter(lightParameter);
      }
      builder.setBaseIcon(JetgroovyIcons.Groovy.Method);
      return new DelegatedMethod(builder, method);
    }

    @NotNull
    private Condition<PsiMethod> buildCondition(@NotNull PsiAnnotation annotation) {
      Condition<PsiMethod> result = method -> {
        if (method.isConstructor() || method.hasModifierProperty(PsiModifier.STATIC)) return false;

        final PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) return false;

        final String qname = containingClass.getQualifiedName();
        if (CommonClassNames.JAVA_LANG_OBJECT.equals(qname)) return false;
        if (GroovyCommonClassNames.GROOVY_OBJECT.equals(qname)) return false;
        if (GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT.equals(qname)) return false;
        if (overridesObjectOrGroovyObject(method)) return false;

        return true;
      };

      if (!myDeprecated) {
        result = Conditions.and(result, method -> PsiImplUtil.getAnnotation(method, CommonClassNames.JAVA_LANG_DEPRECATED) == null);
      }

      List<String> excludes = GrAnnotationUtil.getStringArrayValue(annotation, "excludes", true);
      if (!excludes.isEmpty()) {
        return Conditions.and(result, method -> !excludes.contains(method.getName()));
      }

      List<String> includes = GrAnnotationUtil.getStringArrayValue(annotation, "includes", true);
      if (!includes.isEmpty()) {
        return Conditions.and(result, method -> includes.contains(method.getName()));
      }

      List<PsiClass> excludeTypes = GrAnnotationUtil.getClassArrayValue(annotation, "excludeTypes", true);
      if (!excludeTypes.isEmpty()) {
        return Conditions.and(result, method -> {
          for (PsiClass excludeProvider : excludeTypes) {
            if (excludeProvider.findMethodBySignature(method, false) != null) {
              return false;
            }
          }
          return true;
        });
      }

      List<PsiClass> includeTypes = GrAnnotationUtil.getClassArrayValue(annotation, "includeTypes", true);
      if (!includeTypes.isEmpty()) {
        return Conditions.and(result, method -> {
          for (PsiClass includeProvider : includeTypes) {
            if (includeProvider.findMethodBySignature(method, false) != null) {
              return true;
            }
          }
          return false;
        });
      }

      return result;
    }
  }

  private static final Set<String> OBJECT_METHODS = ContainerUtil.newHashSet(
    "equals", "hashCode", "getClass", "clone", "toString", "notify", "notifyAll", "wait", "finalize"
  );
  private static final Set<String> GROOVY_OBJECT_METHODS = ContainerUtil.newHashSet(
    "invokeMethod", "getProperty", "setProperty", "getMetaClass", "setMetaClass"
  );

  private static boolean overridesObjectOrGroovyObject(PsiMethod method) {
    if (GroovyObjectTransformationSupport.isGroovyObjectSupportMethod(method)) return true;

    final String name = method.getName();
    if (!OBJECT_METHODS.contains(name) && !GROOVY_OBJECT_METHODS.contains(name)) return false;

    final PsiMethod superMethod = PsiSuperMethodImplUtil.findDeepestSuperMethod(method);
    if (superMethod == null) return false;

    final PsiClass superClass = superMethod.getContainingClass();
    if (superClass == null) return false;

    final String qname = superClass.getQualifiedName();
    return CommonClassNames.JAVA_LANG_OBJECT.equals(qname) || GroovyCommonClassNames.GROOVY_OBJECT.equals(qname);
  }
}
