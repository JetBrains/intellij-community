// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.ast;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;
import org.jetbrains.plugins.groovy.transformations.immutable.GrImmutableUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    final PsiAnnotation tupleConstructor = context.getAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_TUPLE_CONSTRUCTOR);
    final PsiAnnotation mapConstructorAnno = context.getAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_MAP_CONSTRUCTOR);
    final boolean immutable = GrImmutableUtils.hasImmutableAnnotation(typeDefinition);
    final boolean canonical = context.getAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_CANONICAL) != null;
    if (!immutable && !canonical && tupleConstructor == null && mapConstructorAnno == null) {
      return;
    }

    if (tupleConstructor != null &&
        typeDefinition.getCodeConstructors().length > 0 &&
        !PsiUtil.getAnnoAttributeValue(tupleConstructor, TupleConstructorAttributes.FORCE, false)) {
      return;
    }

    String originInfo;
    if (immutable) {
      originInfo = "created by @Immutable";
    }
    else if (canonical) {
      originInfo = "created by @Canonical";
    }
    else if (tupleConstructor != null) {
      originInfo = "created by @TupleConstructor";
    }
    else {
      originInfo = "created by @MapConstructor";
    }

    if (canonical || immutable || tupleConstructor != null) {
      final GrLightMethodBuilder fieldsConstructor = generateFieldConstructor(context, tupleConstructor, immutable, canonical, originInfo);
      context.addMethod(fieldsConstructor);
    }

    List<GrLightMethodBuilder> mapConstructors = generateMapConstructor(typeDefinition, originInfo, context);
    for (GrLightMethodBuilder mapConstructor : mapConstructors) {
      context.addMethod(mapConstructor);
    }
  }

  private static @NotNull List<GrLightMethodBuilder> generateMapConstructor(@NotNull GrTypeDefinition typeDefinition,
                                                                            String originInfo,
                                                                            @NotNull TransformationContext context) {
    if (GroovyConfigUtils.isAtLeastGroovy25(typeDefinition)) {
      PsiAnnotation mapConstructorAnno = typeDefinition.getAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_MAP_CONSTRUCTOR);
      if (mapConstructorAnno == null) {
        return Collections.emptyList();
      }
      GrLightMethodBuilder mapConstructor = new GrLightMethodBuilder(typeDefinition);
      checkContainingClass(context, mapConstructor);
      mapConstructor.setOriginInfo(originInfo);
      Visibility visibility = getVisibility(mapConstructorAnno, mapConstructor, Visibility.PUBLIC);
      mapConstructor.addModifier(visibility.toString());
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
        var noArgConstructor = new GrLightMethodBuilder(typeDefinition);
        checkContainingClass(context, noArgConstructor);
        noArgConstructor.setOriginInfo(originInfo);
        return List.of(mapConstructor, noArgConstructor);
      }
      else {
        return List.of(mapConstructor);
      }
    }
    else {
      final GrLightMethodBuilder mapConstructor = new GrLightMethodBuilder(typeDefinition);
      checkContainingClass(context, mapConstructor);
      mapConstructor.addParameter("args", CommonClassNames.JAVA_UTIL_HASH_MAP);
      mapConstructor.setOriginInfo(originInfo);
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
                                                               boolean canonical,
                                                               @NotNull String originInfo) {
    final GrTypeDefinition typeDefinition = context.getCodeClass();
    final GrLightMethodBuilder fieldsConstructor = new GrLightMethodBuilder(typeDefinition.getManager(), typeDefinition.getName());
    fieldsConstructor.setConstructor(true);
    fieldsConstructor.setNavigationElement(typeDefinition);
    fieldsConstructor.setContainingClass(typeDefinition);

    if (canonical) {
      var modifierList = typeDefinition.getModifierList();
      if (modifierList != null) {
        tupleConstructor =
          new GrLightAnnotation(modifierList, typeDefinition, GroovyCommonClassNames.GROOVY_TRANSFORM_TUPLE_CONSTRUCTOR, Map.of());
      }
    }
    if (immutable) {
      var modifierList = typeDefinition.getModifierList();
      if (modifierList != null) {
        tupleConstructor = new GrLightAnnotation(modifierList, typeDefinition, GroovyCommonClassNames.GROOVY_TRANSFORM_TUPLE_CONSTRUCTOR, Map.of(TupleConstructorAttributes.DEFAULTS, "false"));
      }
    }

    if (tupleConstructor != null) {
      boolean optional = !immutable && PsiUtil.getAnnoAttributeValue(tupleConstructor, TupleConstructorAttributes.DEFAULTS, true);
      Visibility visibility = getVisibility(tupleConstructor, fieldsConstructor, Visibility.PUBLIC);
      fieldsConstructor.addModifier(visibility.toString());
      AffectedMembersCache cache = GrGeneratedConstructorUtils.getAffectedMembersCache(tupleConstructor);

      checkContainingClass(context, fieldsConstructor);

      for (PsiNamedElement element : cache.getAffectedMembers()) {
        GrLightParameter parameter;
        if (element instanceof PsiField) {
          String name = AffectedMembersCache.getExternalName(element);
          parameter = new GrLightParameter(name == null ? "arg" : name, ((PsiField)element).getType(), fieldsConstructor);
        } else if (element instanceof GrMethod) {
          String name = PropertyUtilBase.getPropertyName((PsiMember)element);
          PsiType type = PropertyUtilBase.getPropertyType((PsiMethod)element);
          parameter = new GrLightParameter(name == null ? "arg" : name, type, fieldsConstructor);
        } else {
          parameter = null;
        }
        if (parameter != null) {
          parameter.setOptional(optional);
          fieldsConstructor.addParameter(parameter);
        }
      }
    }
    fieldsConstructor.setOriginInfo(originInfo);
    return fieldsConstructor;
  }

  private static void checkContainingClass(@NotNull TransformationContext context,@NotNull GrLightMethodBuilder constructor) {
    GrTypeDefinition codeClass = context.getCodeClass();
    var modifierList = codeClass.getModifierList();
    if (modifierList == null || context.hasModifierProperty(modifierList, PsiModifier.STATIC)) {
      return;
    }
    PsiClass containingClass = codeClass.getContainingClass();
    if (containingClass == null) {
      return;
    }
    var factory = GroovyPsiElementFactory.getInstance(context.getProject());
    PsiClassType classType = factory.createType(containingClass);
    var justTypeParameter = new EnclosingClassParameter("_containingClass", classType, containingClass);
    constructor.addParameter(justTypeParameter);
  }

  static public class EnclosingClassParameter extends GrLightParameter {
    public EnclosingClassParameter(@NlsSafe @NotNull String name,
                                   @Nullable PsiType type,
                                   @NotNull PsiElement scope) {
      super(name, type, scope);
    }
  }
}
