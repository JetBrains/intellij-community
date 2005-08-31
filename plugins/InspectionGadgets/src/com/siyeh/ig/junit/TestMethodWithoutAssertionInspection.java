/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.junit;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NotNull;

public class TestMethodWithoutAssertionInspection extends ExpressionInspection {

  public String getID() {
    return "JUnitTestMethodWithNoAssertions";
  }

  public String getGroupDisplayName() {
    return GroupNames.JUNIT_GROUP_NAME;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new TestMethodWithoutAssertionVisitor();
  }

  private static class TestMethodWithoutAssertionVisitor extends BaseInspectionVisitor {

    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (!TestUtils.isJUnitTestMethod(method)) {
        return;
      }
      final ContainsAssertionVisitor visitor = new ContainsAssertionVisitor();
      method.accept(visitor);
      if (visitor.containsAssertion()) {
        return;
      }
      registerMethodError(method);
    }


  }
}
