// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.checkers;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
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
  public static String isAnnotationApplicable(@NotNull GrAnnotation annotation, @Nullable PsiAnnotationOwner owner) {
    if (!(owner instanceof PsiElement)) return null;
    PsiElement ownerToUse = owner instanceof PsiModifierList ? ((PsiElement)owner).getParent() : (PsiElement)owner;
    PsiAnnotation.TargetType[] elementTypeFields = GrAnnotationImpl.getApplicableElementTypeFields(ownerToUse);
    if (elementTypeFields.length != 0 && !GrAnnotationImpl.isAnnotationApplicableTo(annotation, elementTypeFields)) {
      String annotationTargetText = JavaErrorMessages.message("annotation.target." + elementTypeFields[0]);
      GrCodeReferenceElement ref = annotation.getClassReference();
      return JavaErrorMessages.message("annotation.not.applicable", ref.getText(), annotationTargetText);
    }

    return null;
  }

  public static boolean checkAnnotationArguments(@NotNull AnnotationHolder holder,
                                                 @NotNull PsiClass annotation,
                                                 @NotNull GrCodeReferenceElement refToHighlight,
                                                 @NotNull GrAnnotationNameValuePair[] attributes,
                                                 boolean checkMissedAttributes) {
    Set<String> usedAttrs = new HashSet<>();

    if (attributes.length > 0) {
      final PsiElement identifier = attributes[0].getNameIdentifierGroovy();
      if (attributes.length == 1 && identifier == null) {
        if (checkAnnotationValue(annotation, attributes[0], "value", usedAttrs, attributes[0].getValue(), holder)) return true;
      }
      else {
        for (GrAnnotationNameValuePair attribute : attributes) {
          final String name = attribute.getName();
          if (name != null) {
            final PsiElement toHighlight = attribute.getNameIdentifierGroovy();
            assert toHighlight != null;
            if (checkAnnotationValue(annotation, toHighlight, name, usedAttrs, attribute.getValue(), holder)) return true;
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
      holder.createErrorAnnotation(refToHighlight, GroovyBundle.message("missed.attributes", StringUtil.join(missedAttrs, ", ")));
      return true;
    }
    return false;
  }

  private static boolean checkAnnotationValue(@NotNull PsiClass annotation,
                                              @NotNull PsiElement identifierToHighlight,
                                              @NotNull String name,
                                              @NotNull Set<? super String> usedAttrs,
                                              @Nullable GrAnnotationMemberValue value,
                                              @NotNull AnnotationHolder holder) {
    if (usedAttrs.contains(name)) {
      holder.createErrorAnnotation(identifierToHighlight, GroovyBundle.message("duplicate.attribute"));
    }

    usedAttrs.add(name);

    final PsiMethod[] methods = annotation.findMethodsByName(name, false);
    if (methods.length == 0) {
      holder.createErrorAnnotation(identifierToHighlight,
                                   GroovyBundle.message("at.interface.0.does.not.contain.attribute", annotation.getQualifiedName(), name));
      return true;
    }
    else {
      final PsiMethod method = methods[0];
      final PsiType ltype = method.getReturnType();
      if (ltype != null && value != null) {
        return checkAnnotationValueByType(holder, value, ltype, true);
      }
    }
    return false;
  }

  public static boolean checkAnnotationValueByType(@NotNull AnnotationHolder holder,
                                                   @NotNull GrAnnotationMemberValue value,
                                                   @Nullable PsiType ltype,
                                                   boolean skipArrays) {
    final GlobalSearchScope resolveScope = value.getResolveScope();
    final PsiManager manager = value.getManager();

    if (value instanceof GrExpression) {
      final PsiType rtype;
      if (value instanceof GrClosableBlock) {
        rtype = PsiType.getJavaLangClass(manager, resolveScope);
      }
      else {
        rtype = ((GrExpression)value).getType();
      }

      if (rtype != null && !checkAnnoTypeAssignable(ltype, rtype, value, skipArrays)) {
        holder.createErrorAnnotation(value,
                                     GroovyBundle.message("cannot.assign", rtype.getPresentableText(), ltype.getPresentableText()));
        return true;
      }
    }

    else if (value instanceof GrAnnotation) {
      final PsiElement resolved = ((GrAnnotation)value).getClassReference().resolve();
      if (resolved instanceof PsiClass) {
        final PsiClassType rtype = JavaPsiFacade.getElementFactory(value.getProject()).createType((PsiClass)resolved, PsiSubstitutor.EMPTY);
        if (!checkAnnoTypeAssignable(ltype, rtype, value, skipArrays)) {
          holder.createErrorAnnotation(value, GroovyBundle.message("cannot.assign", rtype.getPresentableText(), ltype.getPresentableText()));
          return true;
        }
      }
    }

    else if (value instanceof GrAnnotationArrayInitializer) {

      if (ltype instanceof PsiArrayType) {
        final PsiType componentType = ((PsiArrayType)ltype).getComponentType();
        final GrAnnotationMemberValue[] initializers = ((GrAnnotationArrayInitializer)value).getInitializers();
        for (GrAnnotationMemberValue initializer : initializers) {
          if (checkAnnotationValueByType(holder, initializer, componentType, false)) return true;
        }
      }
      else {
        final PsiType rtype = TypesUtil.getTupleByAnnotationArrayInitializer((GrAnnotationArrayInitializer)value);
        if (!checkAnnoTypeAssignable(ltype, rtype, value, skipArrays)) {
          holder.createErrorAnnotation(value, GroovyBundle.message("cannot.assign", rtype.getPresentableText(), ltype.getPresentableText()));
          return true;
        }
      }
    }
    return false;
  }

  private static boolean checkAnnoTypeAssignable(@Nullable PsiType type,
                                                 @Nullable PsiType rtype,
                                                 @NotNull GroovyPsiElement context,
                                                 boolean skipArrays) {
    rtype = TypesUtil.unboxPrimitiveTypeWrapper(rtype);
    if (TypesUtil.isAssignableByMethodCallConversion(type, rtype, context)) return true;

    if (!(type instanceof PsiArrayType && skipArrays)) return false;

    final PsiType componentType = ((PsiArrayType)type).getComponentType();
    return checkAnnoTypeAssignable(componentType, rtype, context, skipArrays);
  }
}
