/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.util.ErrorUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

/**
 * @author Maxim.Medvedev
 */
public class RemoveUnnecessaryBracesInGStringIntention extends GrPsiUpdateIntention {
  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return element -> {
      if (!(element instanceof GrString)) return false;

      if (ErrorUtil.containsError(element)) return false;

      for (GrStringInjection child : ((GrString)element).getInjections()) {
        if (GrStringUtil.checkGStringInjectionForUnnecessaryBraces(child)) return true;
      }
      return false;
    };
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    GrStringUtil.removeUnnecessaryBracesInGString((GrString)element);
  }
}


