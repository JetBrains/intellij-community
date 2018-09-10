// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.codeInspection;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.actions.AbstractBatchSuppressByNoInspectionCommentFix;
import com.intellij.codeInspection.BatchSuppressManager;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;

/**
 * @author peter
 */
public class SuppressForMemberFix extends AbstractBatchSuppressByNoInspectionCommentFix {
  private String myKey;
  private final boolean myForClass;

  public SuppressForMemberFix(String toolId, boolean forClass) {
    super(toolId, false);
    myForClass = forClass;
  }

  @Override
  @Nullable
  public GrDocCommentOwner getContainer(final PsiElement context) {
    if (context == null || context instanceof PsiFile) {
      return null;
    }

    GrDocCommentOwner container = null;

    GrDocComment docComment = PsiTreeUtil.getParentOfType(context, GrDocComment.class);
    if (docComment != null) {
      container = docComment.getOwner();
    }
    if (container == null) {
      container = PsiTreeUtil.getParentOfType(context, GrDocCommentOwner.class);
    }

    while (container instanceof GrAnonymousClassDefinition || container instanceof GrTypeParameter) {
      container = PsiTreeUtil.getParentOfType(container, GrDocCommentOwner.class);
      if (container == null) return null;
    }
    if (myForClass) {
      while (container != null ) {
        final GrTypeDefinition parentClass = PsiTreeUtil.getParentOfType(container, GrTypeDefinition.class);
        if (parentClass == null && container instanceof GrTypeDefinition){
          return container;
        }
        container = parentClass;
      }
    }
    return container;
  }

  @Override
  @NotNull
  public String getText() {
    return myKey != null ? InspectionsBundle.message(myKey) : "Suppress for member";
  }


  @Override
  public boolean isAvailable(@NotNull final Project project, @NotNull final PsiElement context) {
    final GrDocCommentOwner container = getContainer(context);
    myKey = container instanceof PsiClass ? "suppress.inspection.class" : container instanceof PsiMethod ? "suppress.inspection.method" : "suppress.inspection.field";
    return container != null && context.getManager().isInProject(context);
  }

  @Override
  protected boolean replaceSuppressionComments(PsiElement container) {
    return false;
  }

  @Override
  protected void createSuppression(@NotNull Project project, @NotNull PsiElement element, @NotNull PsiElement container)
    throws IncorrectOperationException {
    final GrModifierList modifierList = (GrModifierList)((PsiModifierListOwner)container).getModifierList();
    if (modifierList != null) {
      addSuppressAnnotation(project, modifierList, myID);
    }
    DaemonCodeAnalyzer.getInstance(project).restart();
  }

  private static void addSuppressAnnotation(final Project project, final GrModifierList modifierList, final String id) throws IncorrectOperationException {
    PsiAnnotation annotation = modifierList.findAnnotation(BatchSuppressManager.SUPPRESS_INSPECTIONS_ANNOTATION_NAME);
    final GrExpression toAdd = GroovyPsiElementFactory.getInstance(project).createExpressionFromText("\"" + id + "\"");
    if (annotation != null) {
      final PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue(null);
      if (value instanceof GrAnnotationArrayInitializer) {
        value.add(toAdd);
      } else if (value != null) {
        GrAnnotation anno = GroovyPsiElementFactory.getInstance(project).createAnnotationFromText("@A([])");
        final GrAnnotationArrayInitializer list = (GrAnnotationArrayInitializer)anno.findDeclaredAttributeValue(null);
        list.add(value);
        list.add(toAdd);
        annotation.setDeclaredAttributeValue(null, list);
      }
    }
    else {
      modifierList.addAnnotation(BatchSuppressManager.SUPPRESS_INSPECTIONS_ANNOTATION_NAME).setDeclaredAttributeValue(null, toAdd);
    }
  }

}
