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

package org.jetbrains.plugins.groovy.intentions.style;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.IntentionUtils;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import static org.jetbrains.plugins.groovy.lang.psi.util.PsiElementUtil.*;

/**
 * @author ilyas
 */
public class JavaStylePropertiesInvocationIntention extends Intention {
  @Override
  protected boolean isStopElement(PsiElement element) {
    return super.isStopElement(element) || element instanceof GrClosableBlock;
  }

  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    assert element instanceof GrMethodCallExpression || element instanceof GrApplicationStatement;
    GrCall call = ((GrCall) element);
    GrExpression invoked = call instanceof GrMethodCallExpression ?
        ((GrMethodCallExpression) call).getInvokedExpression() :
        ((GrApplicationStatement) call).getFunExpression();
    if (isGetterInvocation(call) && invoked instanceof GrReferenceExpression) {
      String name = ((GrReferenceExpression) invoked).getName();
      assert name != null;
      name = StringUtil.trimStart(name, GETTER_PREFIX);
      name = StringUtil.decapitalize(name);
      replaceWithGetter(((GrMethodCallExpression) call), name);
    } else if (isSetterInvocation(call) && invoked instanceof GrReferenceExpression) {
      String name = ((GrReferenceExpression) invoked).getName();
      assert name != null;
      name = StringUtil.trimStart(name, SETTER_PREFIX);
      name = StringUtil.decapitalize(name);
      GrExpression value;
      if (call instanceof GrMethodCallExpression) {
        GrArgumentList args = call.getArgumentList();
        assert args != null;
        value = args.getExpressionArguments()[0];
      } else {
        GrArgumentList args = call.getArgumentList();
        assert args != null;
        value = args.getExpressionArguments()[0];
      }
      replaceWithSetter(call, name, value);
    }
  }

  private static void replaceWithSetter(GrCall call, String name, GrExpression value) throws IncorrectOperationException {
    GrReferenceExpression refExpr = (GrReferenceExpression) (call instanceof GrMethodCallExpression ?
        ((GrMethodCallExpression) call).getInvokedExpression() :
        ((GrApplicationStatement) call).getFunExpression());
    String oldNameStr = refExpr.getReferenceNameElement().getText();
    String newRefExpr = StringUtil.trimEnd(refExpr.getText(), oldNameStr) + name;
    IntentionUtils.replaceStatement(newRefExpr + " = " + value.getText(), ((GrStatement) call));
  }

  private static void replaceWithGetter(GrMethodCallExpression call, String name) throws IncorrectOperationException {
    GrReferenceExpression refExpr = (GrReferenceExpression) call.getInvokedExpression();
    String oldNameStr = refExpr.getReferenceNameElement().getText();
    String newRefExpr = StringUtil.trimEnd(refExpr.getText(), oldNameStr) + name;
    IntentionUtils.replaceExpression(newRefExpr, call);
  }

  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new JavaPropertyInvocationPredicate();
  }

  private static class JavaPropertyInvocationPredicate implements PsiElementPredicate {
    public boolean satisfiedBy(PsiElement element) {
      if (!(element instanceof GrCall)) return false;
      GrCall call = (GrCall) element;
      return isPropertyAccessor(call);
    }
  }
}
