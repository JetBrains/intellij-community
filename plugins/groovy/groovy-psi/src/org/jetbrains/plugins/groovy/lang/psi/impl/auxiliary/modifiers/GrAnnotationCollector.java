// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightAnnotation;
import org.jetbrains.plugins.groovy.transformations.immutable.GrImmutableUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.singletonMap;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.*;
import static org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyImports.getAliasedShortNames;

public class GrAnnotationCollector {

  @NotNull
  public static GrAnnotation[] getResolvedAnnotations(@NotNull GrModifierList modifierList) {
    final GrAnnotation[] rawAnnotations = modifierList.getRawAnnotations();

    if (!mayHaveAnnotationCollector(rawAnnotations)) return rawAnnotations;

    final List<GrAnnotation> result = ContainerUtil.newArrayList();
    for (GrAnnotation annotation : rawAnnotations) {
      final PsiAnnotation annotationCollector = findAnnotationCollector(annotation);
      if (annotationCollector != null) {
        if (!collectCompileDynamic(result, annotation)) {
          collectAnnotations(result, annotation, annotationCollector);
        }
      }
      else if (!collectHardcoded(result, annotation)) {
        result.add(annotation);
      }
    }

    return result.toArray(GrAnnotation.EMPTY_ARRAY);
  }

  /**
   *
   * @param list resulting collection of aliased annotations
   * @param alias alias annotation
   * @param annotationCollector @AnnotationCollector annotation used in alias declaration
   * @return set of used arguments of alias annotation
   */
  @NotNull
  public static Set<String> collectAnnotations(@NotNull List<GrAnnotation> list,
                                               @NotNull GrAnnotation alias,
                                               @NotNull PsiAnnotation annotationCollector) {

    final PsiModifierList modifierList = (PsiModifierList)annotationCollector.getParent();

    Map<String, Map<String, PsiNameValuePair>> annotations = ContainerUtil.newLinkedHashMap();
    collectAliasedAnnotationsFromAnnotationCollectorValueAttribute(annotationCollector, annotations);
    collectAliasedAnnotationsFromAnnotationCollectorAnnotations(modifierList, annotations);

    final PsiManager manager = alias.getManager();
    final GrAnnotationNameValuePair[] attributes = alias.getParameterList().getAttributes();

    Set<String> allUsedAttrs = ContainerUtil.newLinkedHashSet();
    for (Map.Entry<String, Map<String, PsiNameValuePair>> entry : annotations.entrySet()) {
      final String qname = entry.getKey();
      final PsiClass resolved = JavaPsiFacade.getInstance(alias.getProject()).findClass(qname, alias.getResolveScope());
      if (resolved == null) continue;

      final GrLightAnnotation annotation = new GrLightAnnotation(manager, alias.getLanguage(), qname, modifierList);

      Set<String> usedAttrs = ContainerUtil.newLinkedHashSet();
      for (GrAnnotationNameValuePair attr : attributes) {
        final String name = attr.getName() != null ? attr.getName() : "value";
        if (resolved.findMethodsByName(name, false).length > 0) {
          annotation.addAttribute(attr);
          allUsedAttrs.add(name);
          usedAttrs.add(name);
        }
      }


      final Map<String, PsiNameValuePair> defaults = entry.getValue();
      for (Map.Entry<String, PsiNameValuePair> defa : defaults.entrySet()) {
        if (!usedAttrs.contains(defa.getKey())) {
          annotation.addAttribute(defa.getValue());
        }
      }


      list.add(annotation);
    }

    return allUsedAttrs;
  }

  private static void collectAliasedAnnotationsFromAnnotationCollectorAnnotations(@NotNull PsiModifierList modifierList,
                                                                                  @NotNull Map<String, Map<String, PsiNameValuePair>> annotations) {
    PsiAnnotation[] rawAnnotations = modifierList instanceof GrModifierList ? ((GrModifierList)modifierList).getRawAnnotations()
                                                                            : modifierList.getAnnotations();
    for (PsiAnnotation annotation : rawAnnotations) {
      final String qname = annotation.getQualifiedName();

      if (qname == null || qname.equals(GROOVY_TRANSFORM_ANNOTATION_COLLECTOR) || qname.startsWith("java.lang.annotation")) continue;

      final PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
      for (PsiNameValuePair pair : attributes) {
        Map<String, PsiNameValuePair> map = annotations.get(qname);
        if (map == null) {
          map = ContainerUtil.newLinkedHashMap();
          annotations.put(qname, map);
        }

        map.put(pair.getName() != null ? pair.getName() : "value", pair);
      }
      if (attributes.length == 0 && !annotations.containsKey(qname)) {
        annotations.put(qname, ContainerUtil.newLinkedHashMap());
      }
    }
  }

  private static void collectAliasedAnnotationsFromAnnotationCollectorValueAttribute(@NotNull PsiAnnotation annotationCollector,
                                                                                     @NotNull Map<String, Map<String, PsiNameValuePair>> annotations) {
    final PsiAnnotationMemberValue annotationsFromValue = annotationCollector.findAttributeValue("value");

    if (annotationsFromValue instanceof GrAnnotationArrayInitializer) {
      for (GrAnnotationMemberValue member : ((GrAnnotationArrayInitializer)annotationsFromValue).getInitializers()) {
        if (member instanceof GrReferenceExpression) {
          final PsiElement resolved = ((GrReferenceExpression)member).resolve();
          if (resolved instanceof PsiClass && ((PsiClass)resolved).isAnnotationType()) {
            annotations.put(((PsiClass)resolved).getQualifiedName(), ContainerUtil.newLinkedHashMap());
          }
        }
      }
    }
  }

  @Nullable
  public static PsiAnnotation findAnnotationCollector(@Nullable PsiClass clazz) {
    if (clazz != null) {
      final PsiModifierList modifierList = clazz.getModifierList();
      if (modifierList != null) {
        PsiAnnotation[] annotations = modifierList instanceof GrModifierList ? ((GrModifierList)modifierList).getRawAnnotations() : modifierList.getAnnotations();
        for (PsiAnnotation annotation : annotations) {
          if (GROOVY_TRANSFORM_ANNOTATION_COLLECTOR.equals(annotation.getQualifiedName())) {
            return annotation;
          }
        }
      }
    }

    return null;
  }

  @Nullable
  public static PsiAnnotation findAnnotationCollector(@NotNull GrAnnotation annotation) {
    if (!mayHaveAnnotationCollector(annotation)) {
      return null;
    }
    PsiElement resolved = annotation.getClassReference().resolve();
    if (resolved instanceof ClsClassImpl) {
      return findAnnotationCollector(((ClsClassImpl)resolved).getSourceMirrorClass());
    }
    else if (resolved instanceof PsiClass) {
      return findAnnotationCollector((PsiClass)resolved);
    }
    else {
      return null;
    }
  }

  private static boolean mayHaveAnnotationCollector(@NotNull GrAnnotation[] rawAnnotations) {
    for (GrAnnotation annotation : rawAnnotations) {
      if (mayHaveAnnotationCollector(annotation)) {
        return true;
      }
    }
    return false;
  }

  private static boolean mayHaveAnnotationCollector(@NotNull GrAnnotation annotation) {
    String shortName = annotation.getShortName();
    Set<String> allNames = allCollectorNames(annotation.getProject());
    return allNames.contains(shortName) ||
           ContainerUtil.exists(getAliasedShortNames(annotation, shortName), allNames::contains);
  }

  private static Set<String> allCollectorNames(@NotNull Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      Set<String> result = new HashSet<>();
      GlobalSearchScope scope = GlobalSearchScope.allScope(project);
      for (PsiClass collector : JavaPsiFacade.getInstance(project).findClasses(GROOVY_TRANSFORM_ANNOTATION_COLLECTOR, scope)) {
        AnnotatedElementsSearch.searchPsiClasses(collector, scope).forEach(aClass -> {
          ContainerUtil.addIfNotNull(result, aClass.getName());
          return true;
        });
      }
      return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  private static boolean collectHardcoded(@NotNull List<GrAnnotation> list, @NotNull GrAnnotation alias) {
    String fqn = alias.getQualifiedName();
    if (GROOVY_TRANSFORM_IMMUTABLE.equals(fqn)) {
      GrImmutableUtils.collectImmutableAnnotations(alias, list);
      return true;
    }
    return collectCompileDynamic(list, alias);
  }

  private static boolean collectCompileDynamic(@NotNull List<GrAnnotation> list, @NotNull GrAnnotation alias) {
    if (GROOVY_TRANSFORM_COMPILE_DYNAMIC.equals(alias.getQualifiedName())) {
      PsiAnnotationOwner owner = alias.getOwner();
      if (owner != null) {
        GrLightAnnotation annotation = new GrLightAnnotation(
          owner, alias,
          GROOVY_TRANSFORM_COMPILE_STATIC,
          singletonMap("value", "TypeCheckingMode.SKIP")
        );
        list.add(annotation);
      }
      return true;
    }
    return false;
  }
}
