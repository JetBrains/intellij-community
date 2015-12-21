/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve.ast;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightParameter;
import com.intellij.psi.impl.source.tree.java.PsiCompositeModifierList;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashSet;
import gnu.trove.THashMap;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.*;

/**
 * @author Max Medvedev
 */
public class DelegatedMethodsContributor extends AstTransformContributor {

  @Override
  public void collectMethods(@NotNull final GrTypeDefinition clazz, @NotNull Collection<PsiMethod> collector) {
    Set<PsiClass> processed = new HashSet<PsiClass>();

    if (!checkForDelegate(clazz)) return;

    Map<MethodSignature, PsiMethod> signatures = new THashMap<MethodSignature, PsiMethod>(MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY);
    initializeSignatures(clazz, PsiSubstitutor.EMPTY, signatures, processed);

    List<PsiMethod> methods = new ArrayList<PsiMethod>();
    process(clazz, PsiSubstitutor.EMPTY, true, new HashSet<PsiClass>(), processed, methods, clazz, false);

    final Set<PsiMethod> result = new LinkedHashSet<PsiMethod>();
    for (PsiMethod method : methods) {
      addMethodChecked(signatures, method, PsiSubstitutor.EMPTY, result);
    }

    collector.addAll(result);
  }

  private static boolean checkForDelegate(GrTypeDefinition clazz) {
    for (GrField field : clazz.getFields()) {
      if (PsiImplUtil.getAnnotation(field, GroovyCommonClassNames.GROOVY_LANG_DELEGATE) != null) return true;
    }
    return false;
  }

  /**
   * Adds 'method' to 'signatures' if it doesn't yet contain any method with the same signature or replaces abstract methods
   */
  private static void addMethodChecked(Map<MethodSignature, PsiMethod> signatures,
                                       PsiMethod method,
                                       PsiSubstitutor substitutor,
                                       @Nullable Set<PsiMethod> resultSet) {
    if (method.isConstructor()) return;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return;

    final MethodSignature signature = method.getSignature(substitutor);
    final PsiMethod old = signatures.get(signature);

    if (old != null) {
      //if (method.hasModifierProperty(PsiModifier.ABSTRACT)) return;
      if (!old.hasModifierProperty(PsiModifier.ABSTRACT)) return;

      if (resultSet != null) resultSet.remove(old);
    }

    signatures.put(signature, method);
    if (resultSet != null) resultSet.add(method);
  }

  /**
   * Adds all code methods of clazz add its super classes to signatures. Doesn't walk into interfaces because all methods from them will be overloaded in any case.
   * Besides Some of interfaces came from delegates and they should be visited during the following processing.
   *
   * @param clazz current class
   * @param substitutor super class substitutor of clazz
   * @param signatures map to initialize
   * @param classes already visited classes
   */
  private static void initializeSignatures(PsiClass clazz, PsiSubstitutor substitutor, Map<MethodSignature, PsiMethod> signatures, Set<PsiClass> classes) {
    if (clazz.isInterface()) return;

    if (classes.add(clazz)) {
      final List<PsiMethod> methods;
      if (clazz instanceof GrTypeDefinition) {
        methods = new ArrayList<PsiMethod>();
        GrClassImplUtil.collectMethodsFromBody((GrTypeDefinition)clazz, methods);
      }
      else {
        methods = Arrays.asList(clazz.getMethods());
      }

      for (PsiMethod method : methods) {
        addMethodChecked(signatures, method, substitutor, null);
      }

      for (PsiClassType type : getSuperTypes(clazz)) {
        final PsiClassType.ClassResolveResult result = type.resolveGenerics();
        final PsiClass superClass = result.getElement();
        if (superClass == null) continue;
        final PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, clazz, substitutor);
        initializeSignatures(superClass, superClassSubstitutor, signatures, classes);
      }
    }
  }

  /**
   *  The key method of contributor. It collects all delegating methods of clazz
   *
   * @param clazz class to process
   * @param processedWithoutDeprecated already visited classes which deprecated methods were not processsed
   * @param processedAll already visited classes which all methods were processed
   * @param collector result collection
   */
  private static void process(PsiClass clazz,
                              PsiSubstitutor superClassSubstitutor,
                              boolean shouldProcessDeprecated,
                              Set<PsiClass> processedWithoutDeprecated,
                              Set<PsiClass> processedAll,
                              List<PsiMethod> collector,
                              GrTypeDefinition classToDelegateTo,
                              boolean keepParameterAnnotations) {
    final List<PsiMethod> result = new ArrayList<PsiMethod>();

    //process super methods before delegated methods
    for (PsiClassType superType : getSuperTypes(clazz)) {
      processClassInner(superType, superClassSubstitutor, shouldProcessDeprecated, result, classToDelegateTo, processedWithoutDeprecated,
                        processedAll, keepParameterAnnotations);
    }

    if (clazz instanceof GrTypeDefinition) {
      //search for @Delegate fields and collect methods from them
      for (GrField field : ((GrTypeDefinition)clazz).getFields()) {
        final PsiAnnotation delegate = PsiImplUtil.getAnnotation(field, GroovyCommonClassNames.GROOVY_LANG_DELEGATE);
        if (delegate == null) continue;

        final PsiType type = field.getDeclaredType();
        if (!(type instanceof PsiClassType)) continue;

        processClassInner((PsiClassType)type, superClassSubstitutor, shouldDelegateDeprecated(delegate), result, classToDelegateTo,
                          processedWithoutDeprecated, processedAll, shouldKeepParameterAnnotations(delegate));
      }
    }

    collector.addAll(result);
  }

  private static List<PsiClassType> getSuperTypes(PsiClass clazz) {
    if (clazz instanceof GrTypeDefinition) {
      final GrExtendsClause elist = ((GrTypeDefinition)clazz).getExtendsClause();
      final GrImplementsClause ilist = ((GrTypeDefinition)clazz).getImplementsClause();

      if (elist == null && ilist == null) return ContainerUtil.emptyList();

      final ArrayList<PsiClassType> types = new ArrayList<PsiClassType>();
      if (elist != null) ContainerUtil.addAll(types, elist.getReferencedTypes());
      if (ilist != null) ContainerUtil.addAll(types, ilist.getReferencedTypes());
      return types;
    }
    else {
      final PsiReferenceList elist = clazz.getExtendsList();
      final PsiReferenceList ilist = clazz.getImplementsList();

      if (elist == null && ilist == null) return ContainerUtil.emptyList();

      final ArrayList<PsiClassType> types = new ArrayList<PsiClassType>();
      if (elist != null) ContainerUtil.addAll(types, elist.getReferencedTypes());
      if (ilist != null) ContainerUtil.addAll(types, ilist.getReferencedTypes());
      return types;
    }
  }

  private static void processClassInner(PsiClassType type,
                                        PsiSubstitutor superClassSubstitutor,
                                        boolean shouldProcessDeprecated,
                                        List<PsiMethod> result,
                                        GrTypeDefinition classToDelegateTo,
                                        Set<PsiClass> processedWithoutDeprecated,
                                        Set<PsiClass> processedAll, boolean keepParameterAnnotationsNew) {
    final PsiClassType.ClassResolveResult resolveResult = type.resolveGenerics();
    final PsiClass psiClass = resolveResult.getElement();
    if (psiClass == null) return;

    final String qname = psiClass.getQualifiedName();
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(qname)) return;
    if (GroovyCommonClassNames.GROOVY_OBJECT.equals(qname)) return;
    if (GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT.equals(qname)) return;

    final PsiSubstitutor substitutor = TypesUtil.composeSubstitutors(resolveResult.getSubstitutor(), superClassSubstitutor);

    if (processedAll.contains(psiClass)) return;
    if (!shouldProcessDeprecated && processedWithoutDeprecated.contains(psiClass)) return;

    if (shouldProcessDeprecated) {
      processedAll.add(psiClass);
    }
    else {
      processedWithoutDeprecated.add(psiClass);
    }

    collectMethods(psiClass, substitutor, shouldProcessDeprecated, classToDelegateTo, result, keepParameterAnnotationsNew);
    process(psiClass, substitutor, shouldProcessDeprecated, processedWithoutDeprecated, processedAll, result, classToDelegateTo, keepParameterAnnotationsNew);
  }

  private static void collectMethods(PsiClass currentClass,
                                     PsiSubstitutor currentClassSubstitutor,
                                     boolean shouldProcessDeprecated,
                                     GrTypeDefinition classToDelegateTo,
                                     Collection<PsiMethod> collector,
                                     boolean keepParameterAnnotations) {
    final List<PsiMethod> methods;
    if (currentClass instanceof GrTypeDefinition) {
      methods = new ArrayList<PsiMethod>();
      GrClassImplUtil.collectMethodsFromBody((GrTypeDefinition)currentClass, methods);
    }
    else {
      methods = Arrays.asList(currentClass.getMethods());
    }

    for (PsiMethod method : methods) {
      if (method.isConstructor() || method.hasModifierProperty(PsiModifier.STATIC)) continue;
      if (overridesObjectOrGroovyObject(method)) continue;
      if (!shouldProcessDeprecated && PsiImplUtil.getAnnotation(method, CommonClassNames.JAVA_LANG_DEPRECATED) != null) continue;
      collector.add(generateDelegateMethod(method, classToDelegateTo, currentClassSubstitutor, keepParameterAnnotations));
    }
  }

  private static boolean overridesObjectOrGroovyObject(PsiMethod method) {
    final String name = method.getName();
    if (!OBJECT_METHODS.contains(name) && !GROOVY_OBJECT_METHODS.contains(name)) return false;

    final PsiMethod superMethod = PsiSuperMethodImplUtil.findDeepestSuperMethod(method);
    if (superMethod == null) return false;

    final PsiClass superClass = superMethod.getContainingClass();
    if (superClass == null) return false;

    final String qname = superClass.getQualifiedName();
    return CommonClassNames.JAVA_LANG_OBJECT.equals(qname) || GroovyCommonClassNames.GROOVY_OBJECT.equals(qname);
  }

  private static boolean shouldDelegateDeprecated(PsiAnnotation delegate) {
    final Boolean result = GrAnnotationUtil.inferBooleanAttribute(delegate, "deprecated");
    return result != null && result.booleanValue();
  }

  private static boolean shouldKeepParameterAnnotations(PsiAnnotation delegate) {
    final Boolean keepParameterAnnotations = GrAnnotationUtil.inferBooleanAttribute(delegate, "parameterAnnotations");
    return keepParameterAnnotations != null && keepParameterAnnotations.booleanValue();
  }


  private static PsiMethod generateDelegateMethod(PsiMethod method,
                                                  PsiClass superClass,
                                                  PsiSubstitutor substitutor,
                                                  boolean keepParameterAnnotations) {
    final LightMethodBuilder builder = new LightMethodBuilder(superClass.getManager(), GroovyLanguage.INSTANCE, method.getName());
    builder.setContainingClass(superClass);
    builder.setMethodReturnType(substitutor.substitute(method.getReturnType()));
    builder.setNavigationElement(method);
    builder.addModifier(PsiModifier.PUBLIC);

    final PsiTypeParameter[] typeParameters = method.getTypeParameters();

    final PsiClass containingClass = method.getContainingClass();
    boolean isRaw = containingClass != null && PsiUtil.isRawSubstitutor(containingClass, substitutor);
    if (isRaw) {
      substitutor = JavaPsiFacade.getInstance(method.getProject()).getElementFactory().createRawSubstitutor(substitutor, typeParameters);
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
        type = TypesUtil.getJavaLangObject(superClass);
      }
      final LightParameter lightParameter = new LightParameter(StringUtil.notNullize(originalParameter.getName(), "p" + i), type, builder, JavaLanguage.INSTANCE);
      if (keepParameterAnnotations) {
        final PsiCompositeModifierList delegatingModifierList = new PsiCompositeModifierList(method.getManager(), Collections.singletonList(originalParameter.getModifierList()));
        lightParameter.setModifierList(delegatingModifierList);
      }
      builder.addParameter(lightParameter);
    }
    builder.setBaseIcon(JetgroovyIcons.Groovy.Method);

    return new DelegatedMethod(builder, method);
  }

  private static final Set<String> OBJECT_METHODS = ContainerUtil.newHashSet("equals", "hashCode", "getClass", "clone", "toString", "notify", "notifyAll", "wait", "finalize");
  private static final Set<String> GROOVY_OBJECT_METHODS = ContainerUtil.newHashSet("invokeMethod", "getProperty", "setProperty", "getMetaClass", "setMetaClass");

  @Override
  public void collectImplementsTypes(GrTypeDefinition grType, Collection<PsiClassType> result) {
    final GrField[] fields = grType.getCodeFields();
    for (GrField field : fields) {
      final PsiAnnotation delegate = PsiImplUtil.getAnnotation(field, GroovyCommonClassNames.GROOVY_LANG_DELEGATE);
      if (delegate == null) continue;

      final boolean shouldImplement = shouldImplementDelegatedInterfaces(delegate);
      if (!shouldImplement) continue;

      final PsiType type = field.getDeclaredType();
      if (!(type instanceof PsiClassType)) continue;

      final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
      final PsiClass psiClass = resolveResult.getElement();
      if (psiClass == null) continue;
      final PsiSubstitutor substitutor = resolveResult.getSubstitutor();

      if (psiClass.isInterface()) {
        result.add((PsiClassType)type);
      }
      else {
        for (PsiClassType implementsType : psiClass.getImplementsListTypes()) {
          PsiType substituted = substitutor.substitute(implementsType);
          if (substituted instanceof PsiClassType) {
            result.add((PsiClassType)substituted);
          }
        }
      }
    }
  }

  private static boolean shouldImplementDelegatedInterfaces(PsiAnnotation delegate) {
    final Boolean result = GrAnnotationUtil.inferBooleanAttribute(delegate, "interfaces");
    return result == null || result.booleanValue();
  }
}
