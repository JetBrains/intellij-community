// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.ast;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;
import org.jetbrains.plugins.groovy.transformations.immutable.GrImmutableUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.jetbrains.plugins.groovy.lang.resolve.ast.GrVisibilityUtils.getVisibility;
/**
 * @author peter
 */
public class ConstructorAnnotationsProcessor implements AstTransformationSupport {

  @Override
  public void applyTransformation(@NotNull TransformationContext context) {
    GrTypeDefinition typeDefinition = context.getCodeClass();
    if (typeDefinition.getName() == null) return;
    PsiModifierList modifierList = typeDefinition.getModifierList();
    if (modifierList == null) return;

    final PsiAnnotation tupleConstructor = modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_TUPLE_CONSTRUCTOR);
    final PsiAnnotation mapConstructorAnno = modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_MAP_CONSTRUCTOR);
    final boolean immutable = GrImmutableUtils.hasImmutableAnnotation(typeDefinition);
    final boolean canonical = modifierList.hasAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_CANONICAL);
    if (!immutable && !canonical && tupleConstructor == null && mapConstructorAnno == null) {
      return;
    }

    if (tupleConstructor != null &&
        typeDefinition.getCodeConstructors().length > 0 &&
        !PsiUtil.getAnnoAttributeValue(tupleConstructor, TupleConstructorAttributes.FORCE, false)) {
      return;
    }

    final GrLightMethodBuilder fieldsConstructor = generateFieldConstructor(context, tupleConstructor, immutable, canonical);
    context.addMethod(fieldsConstructor);

    List<GrLightMethodBuilder> mapConstructors = generateMapConstructor(typeDefinition);
    for (GrLightMethodBuilder mapConstructor : mapConstructors) {
      context.addMethod(mapConstructor);
    }
  }

  private static @NotNull List<GrLightMethodBuilder> generateMapConstructor(@NotNull GrTypeDefinition typeDefinition) {
    if (GroovyConfigUtils.isAtLeastGroovy25(typeDefinition)) {
      PsiAnnotation mapConstructorAnno = typeDefinition.getAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_MAP_CONSTRUCTOR);
      if (mapConstructorAnno == null) {
        return Collections.emptyList();
      }
      GrLightMethodBuilder mapConstructor = new GrLightMethodBuilder(typeDefinition);
      var specialParamHandling = GrAnnotationUtil.inferBooleanAttribute(mapConstructorAnno, "specialNamedArgHandling");
      String parameterRepresentation;
      if (Boolean.TRUE.equals(specialParamHandling)) {
        parameterRepresentation = computeMapParameterPresentation(typeDefinition);
      }
      else {
        parameterRepresentation = CommonClassNames.JAVA_UTIL_MAP;
      }
      mapConstructor.addParameter("args", parameterRepresentation);
      var noArg = GrAnnotationUtil.inferBooleanAttribute(mapConstructorAnno, "noArg");
      if (Boolean.TRUE.equals(noArg)) {
        return List.of(mapConstructor, new GrLightMethodBuilder(typeDefinition));
      }
      else {
        return List.of(mapConstructor);
      }
    }
    else {
      final GrLightMethodBuilder mapConstructor = new GrLightMethodBuilder(typeDefinition);
      mapConstructor.addParameter("args", CommonClassNames.JAVA_UTIL_HASH_MAP);
      return List.of(mapConstructor);
    }
  }

  private static @NlsSafe @NotNull String computeMapParameterPresentation(@NotNull GrTypeDefinition clazz) {
    GrField[] fields = clazz.getCodeFields();
    if (fields.length != 1) return CommonClassNames.JAVA_UTIL_MAP;
    PsiType fieldType = fields[0].getDeclaredType();
    if (fieldType == null ||
        fieldType.equalsToText(CommonClassNames.JAVA_UTIL_HASH_MAP) ||
        fieldType.equalsToText(CommonClassNames.JAVA_UTIL_MAP) ||
        fieldType.equalsToText("java.util.AbstractMap") ||
        fieldType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
      return CommonClassNames.JAVA_UTIL_LINKED_HASH_MAP;
    }
    else {
      return CommonClassNames.JAVA_UTIL_MAP;
    }
  }


  @NotNull
  private static GrLightMethodBuilder generateFieldConstructor(@NotNull TransformationContext context,
                                                               @Nullable PsiAnnotation tupleConstructor,
                                                               boolean immutable,
                                                               boolean canonical) {
    final GrTypeDefinition typeDefinition = context.getCodeClass();
    final GrLightMethodBuilder fieldsConstructor = new GrLightMethodBuilder(typeDefinition.getManager(), typeDefinition.getName());
    fieldsConstructor.setConstructor(true);
    fieldsConstructor.setNavigationElement(typeDefinition);
    fieldsConstructor.setContainingClass(typeDefinition);

    GeneratedConstructorCollector collector = new GeneratedConstructorCollector(tupleConstructor, immutable, fieldsConstructor);

    if (tupleConstructor != null) {
      Visibility visibility = getVisibility(tupleConstructor, fieldsConstructor, Visibility.PUBLIC);
      fieldsConstructor.addModifier(visibility.toString());

      final boolean superFields = PsiUtil.getAnnoAttributeValue(tupleConstructor, "includeSuperFields", false);
      final boolean superProperties = PsiUtil.getAnnoAttributeValue(tupleConstructor, "includeSuperProperties", false);
      if (superFields || superProperties) {
        PsiClass superClass = context.getHierarchyView().getSuperClass();
        addParametersForSuper(superClass, collector, new HashSet<>(), superProperties, superFields);
      }
    }
    boolean includeProperties = tupleConstructor == null || PsiUtil.getAnnoAttributeValue(tupleConstructor, "includeProperties", true);
    boolean includeFields = tupleConstructor != null ? PsiUtil.getAnnoAttributeValue(tupleConstructor, "includeFields", false) : !canonical;
    boolean includeBeans = tupleConstructor != null && PsiUtil.getAnnoAttributeValue(tupleConstructor, "allProperties", false);
    collector.accept(typeDefinition, includeProperties, includeBeans, includeFields);

    collector.build(fieldsConstructor);
    if (immutable) {
      fieldsConstructor.setOriginInfo("created by @Immutable");
    }
    else if (tupleConstructor != null) {
      fieldsConstructor.setOriginInfo("created by @TupleConstructor");
    }
    else /*if (canonical != null)*/ {
      fieldsConstructor.setOriginInfo("created by @Canonical");
    }
    return fieldsConstructor;
  }

  private static void addParametersForSuper(@Nullable PsiClass typeDefinition,
                                            @NotNull GeneratedConstructorCollector collector,
                                            Set<? super PsiClass> visited,
                                            boolean includeProperties,
                                            boolean includeFields) {
    if (typeDefinition == null) {
      return;
    }
    if (!visited.add(typeDefinition) || GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT.equals(typeDefinition.getQualifiedName())) {
      return;
    }
    addParametersForSuper(typeDefinition.getSuperClass(), collector, visited, includeProperties, includeFields);
    collector.accept(typeDefinition, includeProperties, false, includeFields);
  }
}
