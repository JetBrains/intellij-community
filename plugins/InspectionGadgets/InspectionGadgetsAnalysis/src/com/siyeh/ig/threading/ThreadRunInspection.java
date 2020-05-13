/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NotNull;

public class ThreadRunInspection extends BaseInspection {
  private static final CallMatcher THREAD_RUN = CallMatcher.instanceCall("java.lang.Thread", "run").parameterCount(0);

  @Override
  @NotNull
  public String getID() {
    return "CallToThreadRun";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("thread.run.problem.descriptor");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new ThreadRunFix();
  }

  private static class ThreadRunFix extends AbstractReplaceWithAnotherMethodCallFix {
    @Override
    protected String getMethodName() {
      return "start";
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThreadRunVisitor();
  }

  private static class ThreadRunVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      super.visitMethodCallExpression(call);
      if (!THREAD_RUN.test(call)) return;
      PsiMethod method = PsiTreeUtil.getParentOfType(call, PsiMethod.class);
      if (THREAD_RUN.methodMatches(method)) return;
      registerMethodCallError(call);
    }
  }
}