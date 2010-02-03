/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicDialog;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicElementSettings;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicPropertyDialog;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * @author Maxim.Medvedev
 */
public class DynamicPropertyFix implements IntentionAction {
  private final GrReferenceExpression myReferenceExpression;
  private final GrArgumentLabel myArgumentLabel;
  private final PsiClass myTargetClass;

  public DynamicPropertyFix(GrReferenceExpression referenceExpression) {
    myReferenceExpression = referenceExpression;
    myArgumentLabel = null;
    myTargetClass = null;
  }

  public DynamicPropertyFix(GrArgumentLabel argumentLabel, PsiClass targetClass) {
    myArgumentLabel = argumentLabel;
    myReferenceExpression = null;
    myTargetClass = targetClass;
  }

  @NotNull
  public String getText() {
    return GroovyBundle.message("add.dynamic.property", getName());
  }

  private String getName() {
    if (myReferenceExpression != null) {
      return myReferenceExpression.getName();
    }
    else {
      return myArgumentLabel.getName();
    }
  }

  @NotNull
  public String getFamilyName() {
    return GroovyBundle.message("add.dynamic.element");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return (myReferenceExpression == null || myReferenceExpression.isValid()) && (myArgumentLabel == null || myArgumentLabel.isValid());
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    DynamicDialog dialog;
    if (myReferenceExpression != null) {
      dialog = new DynamicPropertyDialog(myReferenceExpression);
    }
    else {
      dialog = new DynamicPropertyDialog(myArgumentLabel, myTargetClass);
    }
    dialog.show();
  }

  public void invoke(Project project) throws IncorrectOperationException {
    final DynamicElementSettings settings;
    if (myReferenceExpression != null) {
      settings = QuickfixUtil.createSettings(myReferenceExpression);
    }
    else {
      settings = QuickfixUtil.createSettings(myArgumentLabel, myTargetClass);
    }
    DynamicManager.getInstance(project).addProperty(settings);
  }

  public boolean startInWriteAction() {
    return false;
  }

  public GrReferenceExpression getReferenceExpression() {
    return myReferenceExpression;
  }
}
