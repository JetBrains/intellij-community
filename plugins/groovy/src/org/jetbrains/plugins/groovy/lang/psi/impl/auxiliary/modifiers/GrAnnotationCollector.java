/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers;

import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GrAnnotationCollector {

  @NotNull
  public static GrAnnotation[] getResolvedAnnotations(@NotNull GrModifierList modifierList) {
    final GrAnnotation[] rawAnnotations = modifierList.getRawAnnotations();

    if (!hasAliases(rawAnnotations)) return rawAnnotations;

    List<GrAnnotation> result = ContainerUtil.newArrayList();
    for (GrAnnotation annotation : rawAnnotations) {
      final PsiAnnotation annotationCollector = findAnnotationCollector(annotation);
      if (annotationCollector != null) {
        collectAnnotations(result, annotation, annotationCollector);
      }
      else {
        result.add(annotation);
      }
    }


    return result.toArray(new GrAnnotation[result.size()]);
  }

  private static boolean hasAliases(@NotNull GrAnnotation[] rawAnnotations) {
    for (GrAnnotation annotation : rawAnnotations) {
      final PsiAnnotation annotationCollector = findAnnotationCollector(annotation);
      if (annotationCollector != null) {
        return true;
      }
    }

    return false;
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

    Map<String, Map<String, PsiNameValuePair>> annotations = ContainerUtil.newHashMap();
    collectAliasedAnnotationsFromAnnotationCollectorValueAttribute(annotationCollector, (HashMap<String, Map<String, PsiNameValuePair>>)annotations);
    collectAliasedAnnotationsFromAnnotationCollectorAnnotations(modifierList, (HashMap<String, Map<String, PsiNameValuePair>>)annotations);

    final PsiManager manager = alias.getManager();
    final GrAnnotationNameValuePair[] attributes = alias.getParameterList().getAttributes();

    Set<String> allUsedAttrs = ContainerUtil.newHashSet();
    for (Map.Entry<String, Map<String, PsiNameValuePair>> entry : annotations.entrySet()) {
      final String qname = entry.getKey();
      final PsiClass resolved = JavaPsiFacade.getInstance(alias.getProject()).findClass(qname, alias.getResolveScope());
      if (resolved == null) continue;

      final GrLightAnnotation annotation = new GrLightAnnotation(manager, alias.getLanguage(), qname, modifierList);

      Set<String> usedAttrs = ContainerUtil.newHashSet();
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
                                                                                  @NotNull HashMap<String, Map<String, PsiNameValuePair>> annotations) {
    PsiElement parent = modifierList.getParent();
    if (parent instanceof PsiClass &&
        GroovyCommonClassNames.GROOVY_TRANSFORM_COMPILE_DYNAMIC.equals(((PsiClass)parent).getQualifiedName())) {
      HashMap<String, PsiNameValuePair> params = ContainerUtil.newHashMap();
      annotations.put(GroovyCommonClassNames.GROOVY_TRANSFORM_COMPILE_STATIC, params);
      GrAnnotation annotation =
        GroovyPsiElementFactory.getInstance(modifierList.getProject()).createAnnotationFromText("@CompileStatic(TypeCheckingMode.SKIP)");
      params.put("value", annotation.getParameterList().getAttributes()[0]);
      return;
    }

    PsiAnnotation[] rawAnnotations =
      modifierList instanceof GrModifierList ? ((GrModifierList)modifierList).getRawAnnotations() : modifierList.getAnnotations();
    for (PsiAnnotation annotation : rawAnnotations) {
      final String qname = annotation.getQualifiedName();

      if (qname == null || qname.equals(GroovyCommonClassNames.GROOVY_TRANSFORM_ANNOTATION_COLLECTOR)) continue;

      final PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
      for (PsiNameValuePair pair : attributes) {
        Map<String, PsiNameValuePair> map = annotations.get(qname);
        if (map == null) {
          map = ContainerUtil.newHashMap();
          annotations.put(qname, map);
        }

        map.put(pair.getName() != null ? pair.getName() : "value", pair);
      }
      if (attributes.length == 0 && !annotations.containsKey(qname)) {
        annotations.put(qname, ContainerUtil.<String, PsiNameValuePair>newHashMap());
      }
    }

  }

  private static void collectAliasedAnnotationsFromAnnotationCollectorValueAttribute(@NotNull PsiAnnotation annotationCollector,
                                                                                     @NotNull HashMap<String, Map<String, PsiNameValuePair>> annotations) {
    final PsiAnnotationMemberValue annotationsFromValue = annotationCollector.findAttributeValue("value");

    if (annotationsFromValue instanceof GrAnnotationArrayInitializer) {
      for (GrAnnotationMemberValue member : ((GrAnnotationArrayInitializer)annotationsFromValue).getInitializers()) {
        if (member instanceof GrReferenceExpression) {
          final PsiElement resolved = ((GrReferenceExpression)member).resolve();
          if (resolved instanceof PsiClass && ((PsiClass)resolved).isAnnotationType()) {
            annotations.put(((PsiClass)resolved).getQualifiedName(), ContainerUtil.<String, PsiNameValuePair>newHashMap());
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
          if (GroovyCommonClassNames.GROOVY_TRANSFORM_ANNOTATION_COLLECTOR.equals(annotation.getQualifiedName())) {
            return annotation;
          }
        }
      }
    }

    return null;
  }


  @Nullable
  public static PsiAnnotation findAnnotationCollector(@NotNull GrAnnotation annotation) {
    final GrCodeReferenceElement ref = annotation.getClassReference();

    final PsiElement resolved = ref.resolve();
    if (resolved instanceof PsiClass) {
      return findAnnotationCollector((PsiClass)resolved);
    }
    else {
      return null;
    }
  }
}
