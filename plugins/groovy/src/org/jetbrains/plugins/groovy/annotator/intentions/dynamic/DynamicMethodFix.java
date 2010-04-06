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
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicDialog;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicElementSettings;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicMethodDialog;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Maxim.Medvedev
 */
public class DynamicMethodFix implements IntentionAction {
  private final GrReferenceExpression myReferenceExpression;

  public DynamicMethodFix(GrReferenceExpression referenceExpression) {
    myReferenceExpression = referenceExpression;
  }

  @NotNull
  public String getText() {
    final PsiType[] methodArgumentsTypes = PsiUtil.getArgumentTypes(myReferenceExpression, false);
    StringBuilder builder = new StringBuilder(" '").append(myReferenceExpression.getName());
    builder.append("(");

    assert methodArgumentsTypes != null;
    for (int i = 0; i < methodArgumentsTypes.length; i++) {
      PsiType type = methodArgumentsTypes[i];

      if (i > 0) {
        builder.append(", ");
      }
      builder.append(type.getPresentableText());
    }
    builder.append(")");
    builder.append("' ");

    return GroovyBundle.message("add.dynamic.method") + builder.toString();
  }

  @NotNull
  public String getFamilyName() {
    return GroovyBundle.message("add.dynamic.element");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return myReferenceExpression.isValid();
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    DynamicDialog dialog = new DynamicMethodDialog(myReferenceExpression);
    dialog.show();
  }

  public void invoke(Project project) throws IncorrectOperationException {
    final DynamicElementSettings settings = QuickfixUtil.createSettings(myReferenceExpression);
    DynamicManager.getInstance(project).addMethod(settings);
  }

  public boolean startInWriteAction() {
    return false;
  }

  public GrReferenceExpression getReferenceExpression() {
    return myReferenceExpression;
  }
}
