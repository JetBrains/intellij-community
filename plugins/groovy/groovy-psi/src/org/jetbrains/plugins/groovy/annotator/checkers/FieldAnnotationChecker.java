// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.checkers;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Max Medvedev
 */
public final class FieldAnnotationChecker extends CustomAnnotationChecker {

  @Override
  public boolean checkApplicability(@NotNull AnnotationHolder holder, @NotNull GrAnnotation annotation) {
    final String qname = annotation.getQualifiedName();
    if (!GroovyCommonClassNames.GROOVY_TRANSFORM_FIELD.equals(qname)) return false;

    checkScriptField(holder, annotation);

    PsiElement annoParent = annotation.getParent();
    PsiElement ownerToUse = annoParent instanceof PsiModifierList ? annoParent.getParent() : annoParent;
    if (!(ownerToUse instanceof GrVariableDeclaration declaration)) {
      return false;
    }
    else {
      if (declaration.getVariables().length != 1 || !PsiUtil.isLocalVariable(declaration.getVariables()[0])) {
        return false;
      }
    }

    if (!GrAnnotationImpl.isAnnotationApplicableTo(annotation, PsiAnnotation.TargetType.LOCAL_VARIABLE)) {
      GrCodeReferenceElement ref = annotation.getClassReference();
      String target = JavaPsiBundle.message("annotation.target.LOCAL_VARIABLE");
      String description = JavaErrorBundle.message("annotation.not.applicable", ref.getText(), target);
      holder.newAnnotation(HighlightSeverity.ERROR, description).range(ref).create();
    }

    return true;
  }

  private static void checkScriptField(AnnotationHolder holder, GrAnnotation annotation) {
    final PsiAnnotationOwner owner = annotation.getOwner();
    final GrMember container = PsiTreeUtil.getParentOfType(((PsiElement)owner), GrMember.class);
    if (container != null) {
      String message;
      if (container.getContainingClass() instanceof GroovyScriptClass) {
        message = GroovyBundle.message("annotation.field.can.only.be.used.within.a.script.body");
      }
      else {
        message = GroovyBundle.message("annotation.field.can.only.be.used.within.a.script");
      }
      holder.newAnnotation(HighlightSeverity.ERROR, message).range(annotation).create();
    }
  }

}
