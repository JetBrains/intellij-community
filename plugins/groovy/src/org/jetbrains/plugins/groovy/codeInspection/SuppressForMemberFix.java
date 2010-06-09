/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.codeInspection;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.codeInspection.SuppressManager;
import com.intellij.codeInspection.SuppressManagerImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;

/**
 * @author peter
 */
public class SuppressForMemberFix extends SuppressIntentionAction {
  private final String myID;
  private String myKey;
  private final boolean myForClass;

  public SuppressForMemberFix(HighlightDisplayKey key, boolean forClass) {
    myID = key.getID();
    myForClass = forClass;
  }

  @Nullable
  protected GrDocCommentOwner getContainer(final PsiElement context) {
    if (context == null || context instanceof PsiFile) {
      return null;
    }
    GrDocCommentOwner container = PsiTreeUtil.getParentOfType(context, GrDocCommentOwner.class);
    while (container instanceof GrAnonymousClassDefinition || container instanceof GrTypeParameter) {
      container = PsiTreeUtil.getParentOfType(container, GrDocCommentOwner.class);
      if (container == null) return null;
    }
    if (myForClass) {
      while (container != null ) {
        final GrTypeDefinition parentClass = PsiTreeUtil.getParentOfType(container, GrTypeDefinition.class);
        if ((parentClass == null) && container instanceof GrTypeDefinition){
          return container;
        }
        container = parentClass;
      }
    }
    return container;
  }

  @NotNull
  public String getText() {
    return myKey != null ? InspectionsBundle.message(myKey) : "Suppress for member";
  }


  @NotNull
  public String getFamilyName() {
    return InspectionsBundle.message("suppress.inspection.family");
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, @NotNull final PsiElement context) {
    final GrDocCommentOwner container = getContainer(context);
    myKey = container instanceof PsiClass
            ? "suppress.inspection.class"
            : container instanceof PsiMethod ? "suppress.inspection.method" : "suppress.inspection.field";
    return container != null && context != null && context.getManager().isInProject(context);
  }

  public void invoke(final Project project, final Editor editor, final PsiElement element) throws IncorrectOperationException {
    GrDocCommentOwner container = getContainer(element);
    assert container != null;
    if (!CodeInsightUtilBase.preparePsiElementForWrite(container)) return;
    final GrModifierList modifierList = (GrModifierList)((PsiModifierListOwner)container).getModifierList();
    if (modifierList != null) {
      addSuppressAnnotation(project, modifierList, myID);
    }
    DaemonCodeAnalyzer.getInstance(project).restart();
  }

  private static void addSuppressAnnotation(final Project project, final GrModifierList modifierList, final String id) throws IncorrectOperationException {
    PsiAnnotation annotation = modifierList.findAnnotation(SuppressManagerImpl.SUPPRESS_INSPECTIONS_ANNOTATION_NAME);
    final GrExpression toAdd = GroovyPsiElementFactory.getInstance(project).createExpressionFromText("\"" + id + "\"");
    if (annotation != null) {
      final PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue(null);
      if (value instanceof GrListOrMap) {
        value.add(toAdd);
      } else if (value != null) {
        final GrExpression list = GroovyPsiElementFactory.getInstance(project).createExpressionFromText("[]");
        list.add(value);
        list.add(toAdd);
        annotation.setDeclaredAttributeValue(null, list);
      }
    }
    else {
      modifierList.addAnnotation(SuppressManager.SUPPRESS_INSPECTIONS_ANNOTATION_NAME).setDeclaredAttributeValue(null, toAdd);
    }
  }

}
