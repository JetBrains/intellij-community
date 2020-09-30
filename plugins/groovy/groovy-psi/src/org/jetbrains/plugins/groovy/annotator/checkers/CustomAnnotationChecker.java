// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.checkers;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Max Medvedev
 */
public abstract class CustomAnnotationChecker {
  public static final ExtensionPointName<CustomAnnotationChecker> EP_NAME = ExtensionPointName.create("org.intellij.groovy.customAnnotationChecker");

  public boolean checkArgumentList(@NotNull AnnotationHolder holder, @NotNull GrAnnotation annotation) {return false;}

  public boolean checkApplicability(@NotNull AnnotationHolder holder, @NotNull GrAnnotation annotation) {return false;}

  @Nullable
  @InspectionMessage
  static String checkAnnotationApplicable(@NotNull GrAnnotation annotation, @Nullable PsiAnnotationOwner owner) {
    if (!(owner instanceof PsiElement)) return null;
    PsiElement ownerToUse = owner instanceof PsiModifierList ? ((PsiElement)owner).getParent() : (PsiElement)owner;
    PsiAnnotation.TargetType[] elementTypeFields = GrAnnotationImpl.getApplicableElementTypeFields(ownerToUse);
    if (elementTypeFields.length != 0 && !GrAnnotationImpl.isAnnotationApplicableTo(annotation, elementTypeFields)) {
      String annotationTargetText = JavaAnalysisBundle.message("annotation.target." + elementTypeFields[0]);
      GrCodeReferenceElement ref = annotation.getClassReference();
      return JavaErrorBundle.message("annotation.not.applicable", ref.getText(), annotationTargetText);
    }

    return null;
  }

  public static Pair<PsiElement, @InspectionMessage String> checkAnnotationArguments(@NotNull PsiClass annotation,
                                                                                     GrAnnotationNameValuePair @NotNull [] attributes,
                                                                                     boolean checkMissedAttributes) {
    Set<String> usedAttrs = new HashSet<>();

    if (attributes.length > 0) {
      final PsiElement identifier = attributes[0].getNameIdentifierGroovy();
      if (attributes.length == 1 && identifier == null) {
        Pair.NonNull<PsiElement, String> r =
          checkAnnotationValue(annotation, attributes[0], "value", usedAttrs, attributes[0].getValue());
        if (r != null) return r;
      }
      else {
        for (GrAnnotationNameValuePair attribute : attributes) {
          final String name = attribute.getName();
          if (name != null) {
            final PsiElement toHighlight = attribute.getNameIdentifierGroovy();
            assert toHighlight != null;
            Pair.NonNull<PsiElement, String> r = checkAnnotationValue(annotation, toHighlight, name, usedAttrs, attribute.getValue());
            if (r != null) return r;
          }
        }
      }
    }

    List<String> missedAttrs = new ArrayList<>();
    final PsiMethod[] methods = annotation.getMethods();
    for (PsiMethod method : methods) {
      final String name = method.getName();
      if (usedAttrs.contains(name) ||
          method instanceof PsiAnnotationMethod && ((PsiAnnotationMethod)method).getDefaultValue() != null) {
        continue;
      }
      missedAttrs.add(name);
    }

    if (checkMissedAttributes && !missedAttrs.isEmpty()) {
      return Pair.create(null, GroovyBundle.message("missed.attributes", StringUtil.join(missedAttrs, ", ")));
    }
    return null;
  }

  private static Pair.NonNull<PsiElement, String> checkAnnotationValue(@NotNull PsiClass annotation,
                                                               @NotNull PsiElement identifierToHighlight,
                                                               @NotNull String name,
                                                               @NotNull Set<? super String> usedAttrs,
                                                               @Nullable GrAnnotationMemberValue value) {
    if (!usedAttrs.add(name)) {
      return Pair.createNonNull(identifierToHighlight, GroovyBundle.message("duplicate.attribute"));
    }

    final PsiMethod[] methods = annotation.findMethodsByName(name, false);
    if (methods.length == 0) {
      return Pair.createNonNull(identifierToHighlight,
                                   GroovyBundle.message("at.interface.0.does.not.contain.attribute", annotation.getQualifiedName(), name));
    }
    final PsiMethod method = methods[0];
    final PsiType ltype = method.getReturnType();
    if (ltype != null && value != null) {
      return checkAnnotationValueByType(value, ltype, true);
    }
    return null;
  }

  public static Pair.NonNull<PsiElement, @InspectionMessage String> checkAnnotationValueByType(@NotNull GrAnnotationMemberValue value,
                                                                                               @Nullable PsiType ltype,
                                                                                               boolean skipArrays) {
    final GlobalSearchScope resolveScope = value.getResolveScope();
    final PsiManager manager = value.getManager();

    if (value instanceof GrExpression) {
      final PsiType rtype;
      if (value instanceof GrFunctionalExpression) {
        rtype = PsiType.getJavaLangClass(manager, resolveScope);
      }
      else {
        rtype = ((GrExpression)value).getType();
      }

      if (rtype != null && !isAnnoTypeAssignable(ltype, rtype, value, skipArrays)) {
        return Pair.createNonNull(value, GroovyBundle.message("cannot.assign", rtype.getPresentableText(), ltype.getPresentableText()));
      }
    }

    else if (value instanceof GrAnnotation) {
      final PsiElement resolved = ((GrAnnotation)value).getClassReference().resolve();
      if (resolved instanceof PsiClass) {
        final PsiClassType rtype = JavaPsiFacade.getElementFactory(value.getProject()).createType((PsiClass)resolved, PsiSubstitutor.EMPTY);
        if (!isAnnoTypeAssignable(ltype, rtype, value, skipArrays)) {
          return Pair.createNonNull(value, GroovyBundle.message("cannot.assign", rtype.getPresentableText(), ltype.getPresentableText()));
        }
      }
    }

    else if (value instanceof GrAnnotationArrayInitializer) {
      if (ltype instanceof PsiArrayType) {
        final PsiType componentType = ((PsiArrayType)ltype).getComponentType();
        final GrAnnotationMemberValue[] initializers = ((GrAnnotationArrayInitializer)value).getInitializers();
        for (GrAnnotationMemberValue initializer : initializers) {
          Pair.NonNull<PsiElement, String> r = checkAnnotationValueByType(initializer, componentType, false);
          if (r!=null) return r;
        }
      }
      else {
        final PsiType rtype = TypesUtil.getTupleByAnnotationArrayInitializer((GrAnnotationArrayInitializer)value);
        if (!isAnnoTypeAssignable(ltype, rtype, value, skipArrays)) {
          return Pair.createNonNull(value, GroovyBundle.message("cannot.assign", rtype.getPresentableText(), ltype.getPresentableText()));
        }
      }
    }
    return null;
  }

  private static boolean isAnnoTypeAssignable(@Nullable PsiType type,
                                              @Nullable PsiType rtype,
                                              @NotNull GroovyPsiElement context,
                                              boolean skipArrays) {
    rtype = TypesUtil.unboxPrimitiveTypeWrapper(rtype);
    if (TypesUtil.isAssignableByMethodCallConversion(type, rtype, context)) return true;

    if (!(type instanceof PsiArrayType && skipArrays)) return false;

    final PsiType componentType = ((PsiArrayType)type).getComponentType();
    return isAnnoTypeAssignable(componentType, rtype, context, skipArrays);
  }
}
