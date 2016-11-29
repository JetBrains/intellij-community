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
package org.jetbrains.plugins.groovy.annotator.checkers;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.HashSet;
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
import java.util.List;
import java.util.Map;
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

  public static void checkAnnotationArguments(@NotNull AnnotationHolder holder,
                                              @NotNull PsiClass annotation,
                                              @NotNull GrCodeReferenceElement refToHighlight,
                                              @NotNull GrAnnotationNameValuePair[] attributes,
                                              boolean checkMissedAttributes) {
    Set<String> usedAttrs = new HashSet<>();

    if (attributes.length > 0) {
      final PsiElement identifier = attributes[0].getNameIdentifierGroovy();
      if (attributes.length == 1 && identifier == null) {
        checkAnnotationValue(annotation, attributes[0], "value", usedAttrs, attributes[0].getValue(), holder);
      }
      else {
        for (GrAnnotationNameValuePair attribute : attributes) {
          final String name = attribute.getName();
          if (name != null) {
            final PsiElement toHighlight = attribute.getNameIdentifierGroovy();
            assert toHighlight != null;
            checkAnnotationValue(annotation, toHighlight, name, usedAttrs, attribute.getValue(), holder);
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
    }
  }

  private static void checkAnnotationValue(@NotNull PsiClass annotation,
                                           @NotNull PsiElement identifierToHighlight,
                                           @NotNull String name,
                                           @NotNull Set<String> usedAttrs,
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
    }
    else {
      final PsiMethod method = methods[0];
      final PsiType ltype = method.getReturnType();
      if (ltype != null && value != null) {
        checkAnnotationValueByType(holder, value, ltype, true);
      }
    }
  }

  public static void checkAnnotationValueByType(@NotNull AnnotationHolder holder,
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
      }
    }

    else if (value instanceof GrAnnotation) {
      final PsiElement resolved = ((GrAnnotation)value).getClassReference().resolve();
      if (resolved instanceof PsiClass) {
        final PsiClassType rtype = JavaPsiFacade.getElementFactory(value.getProject()).createType((PsiClass)resolved, PsiSubstitutor.EMPTY);
        if (!checkAnnoTypeAssignable(ltype, rtype, value, skipArrays)) {
          holder.createErrorAnnotation(value, GroovyBundle.message("cannot.assign", rtype.getPresentableText(), ltype.getPresentableText()));
        }
      }
    }

    else if (value instanceof GrAnnotationArrayInitializer) {

      if (ltype instanceof PsiArrayType) {
        final PsiType componentType = ((PsiArrayType)ltype).getComponentType();
        final GrAnnotationMemberValue[] initializers = ((GrAnnotationArrayInitializer)value).getInitializers();
        for (GrAnnotationMemberValue initializer : initializers) {
          checkAnnotationValueByType(holder, initializer, componentType, false);
        }
      }
      else {
        final PsiType rtype = TypesUtil.getTupleByAnnotationArrayInitializer((GrAnnotationArrayInitializer)value);
        if (!checkAnnoTypeAssignable(ltype, rtype, value, skipArrays)) {
          holder.createErrorAnnotation(value, GroovyBundle.message("cannot.assign", rtype.getPresentableText(), ltype.getPresentableText()));
        }
      }
    }
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

  public static void highlightErrors(AnnotationHolder holder, Map<PsiElement, String> errors) {
    for (Map.Entry<PsiElement, String> entry : errors.entrySet()) {
      holder.createErrorAnnotation(entry.getKey(), entry.getValue());
    }
  }
}
