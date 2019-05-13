/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
public class FieldAnnotationChecker extends CustomAnnotationChecker {

  @Override
  public boolean checkApplicability(@NotNull AnnotationHolder holder, @NotNull GrAnnotation annotation) {
    final String qname = annotation.getQualifiedName();
    if (!GroovyCommonClassNames.GROOVY_TRANSFORM_FIELD.equals(qname)) return false;

    checkScriptField(holder, annotation);

    PsiElement annoParent = annotation.getParent();
    PsiElement ownerToUse = annoParent instanceof PsiModifierList ? annoParent.getParent() : annoParent;
    if (!(ownerToUse instanceof GrVariableDeclaration)) {
      return false;
    }
    else {
      GrVariableDeclaration declaration = (GrVariableDeclaration)ownerToUse;
      if (declaration.getVariables().length != 1 || !PsiUtil.isLocalVariable(declaration.getVariables()[0])) {
        return false;
      }
    }

    if (!GrAnnotationImpl.isAnnotationApplicableTo(annotation, PsiAnnotation.TargetType.LOCAL_VARIABLE)) {
      GrCodeReferenceElement ref = annotation.getClassReference();
      String target = JavaErrorMessages.message("annotation.target.LOCAL_VARIABLE");
      String description = JavaErrorMessages.message("annotation.not.applicable", ref.getText(), target);
      holder.createErrorAnnotation(ref, description);
    }

    return true;
  }

  private static void checkScriptField(AnnotationHolder holder, GrAnnotation annotation) {
    final PsiAnnotationOwner owner = annotation.getOwner();
    final GrMember container = PsiTreeUtil.getParentOfType(((PsiElement)owner), GrMember.class);
    if (container != null) {
      if (container.getContainingClass() instanceof GroovyScriptClass) {
        holder.createErrorAnnotation(annotation, GroovyBundle.message("annotation.field.can.only.be.used.within.a.script.body"));
      }
      else {
        holder.createErrorAnnotation(annotation, GroovyBundle.message("annotation.field.can.only.be.used.within.a.script"));
      }
    }
  }

}
