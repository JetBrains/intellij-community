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
package org.intellij.plugins.intelliLang.inject;

import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Gregory.Shrago
 */
public class CustomErrorElementFilter extends HighlightErrorFilter {
  public boolean shouldHighlightErrorElement(@NotNull final PsiErrorElement element) {
    return !value(element);
  }

  public static boolean value(final PsiErrorElement psiErrorElement) {
    return Boolean.TRUE.equals(psiErrorElement.getContainingFile().getUserData(InjectedLanguageUtil.FRANKENSTEIN_INJECTION));
  }
}
