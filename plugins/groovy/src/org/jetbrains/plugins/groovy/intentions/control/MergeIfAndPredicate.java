/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.util.ErrorUtil;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.impl.utils.ConditionalUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;


class MergeIfAndPredicate implements PsiElementPredicate {
  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof GrIfStatement)) {
      return false;
    }
    final GrIfStatement ifStatement = (GrIfStatement) element;
    if (ErrorUtil.containsError(ifStatement)) {
      return false;
    }
    GrStatement thenBranch = ifStatement.getThenBranch();
    if (thenBranch == null) {
      return false;
    }
    thenBranch = ConditionalUtils.stripBraces(thenBranch);
    if (!(thenBranch instanceof GrIfStatement)) {
      return false;
    }
    GrStatement elseBranch = ifStatement.getElseBranch();
    if (elseBranch != null) {
      elseBranch = ConditionalUtils.stripBraces(elseBranch);
      if (elseBranch != null) {
        return false;
      }
    }

    final GrIfStatement childIfStatement = (GrIfStatement) thenBranch;

    return childIfStatement.getElseBranch() == null;
  }
}
