/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.base;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public abstract class MutablyNamedIntention extends Intention {

  private @IntentionName String m_text = null;

  protected abstract @IntentionName String getTextForElement(PsiElement element);

  @Override
  @NotNull
  public final String getText() {
    return m_text == null ? "" : m_text;
  }

  @Override
  public final boolean isAvailable(@NotNull Project project, Editor editor,
                                   @NotNull PsiElement node) {
    final PsiElement element = findMatchingElement(node, editor);
    if (element == null) {
      return false;
    }
    m_text = getTextForElement(element);
    return m_text != null;
  }
}