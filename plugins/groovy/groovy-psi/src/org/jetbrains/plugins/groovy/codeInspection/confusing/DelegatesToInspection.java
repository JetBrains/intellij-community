// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

/**
 * @author Max Medvedev
 */
public class DelegatesToInspection extends BaseInspection {
  @NotNull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitAnnotation(@NotNull GrAnnotation annotation) {
        checkTarget(annotation);
        checkDelegatesTo(annotation);
      }

      private void checkTarget(GrAnnotation annotation) {
        if (!GroovyCommonClassNames.GROOVY_LANG_DELEGATES_TO_TARGET.equals(annotation.getQualifiedName())) return;


        final PsiElement owner = annotation.getParent().getParent();
        if (!(owner instanceof GrParameter)) return;


        final boolean isTargetDeclared = annotation.findDeclaredAttributeValue("value") != null;
        String targetName = GrAnnotationUtil.inferStringAttribute(annotation, "value");

        final GrParameterList parameterList = (GrParameterList)owner.getParent();
        for (GrParameter parameter : parameterList.getParameters()) {
          final PsiAnnotation delegatesTo = parameter.getModifierList().findAnnotation(GroovyCommonClassNames.GROOVY_LANG_DELEGATES_TO);
          if (delegatesTo != null) {
            if (isTargetDeclared) {
              final String curTarget = GrAnnotationUtil.inferStringAttribute(delegatesTo, "target");
              if (curTarget != null && curTarget.equals(targetName)) {
                return; //target is used
              }
            }
            else {
              if (delegatesTo.findDeclaredAttributeValue("target") == null && delegatesTo.findDeclaredAttributeValue("value") == null) {
                return; // target is used
              }
            }
          }
        }

        registerError(annotation.getClassReference(), GroovyBundle.message("target.annotation.is.unused"),
                      LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      }

      private void checkDelegatesTo(GrAnnotation annotation) {
        if (!GroovyCommonClassNames.GROOVY_LANG_DELEGATES_TO.equals(annotation.getQualifiedName())) return;

        final PsiElement owner = annotation.getParent().getParent();
        if (!(owner instanceof GrParameter)) return;

        final PsiAnnotationMemberValue targetPair = annotation.findDeclaredAttributeValue("target");
        if (targetPair == null) return;

        String targetName = GrAnnotationUtil.inferStringAttribute(annotation, "target");

        final GrParameterList parameterList = (GrParameterList)owner.getParent();
        for (GrParameter parameter : parameterList.getParameters()) {
          final PsiAnnotation target = parameter.getModifierList().findAnnotation(GroovyCommonClassNames.GROOVY_LANG_DELEGATES_TO_TARGET);
          if (target != null) {
            final String curTarget = GrAnnotationUtil.inferStringAttribute(target, "value");
            if (curTarget != null && curTarget.equals(targetName)) {
              return; //target is used
            }
          }
        }

        registerError(targetPair, GroovyBundle.message("target.0.does.not.exist", targetName != null ? targetName : "?"),
                      LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      }
    };
  }
}
