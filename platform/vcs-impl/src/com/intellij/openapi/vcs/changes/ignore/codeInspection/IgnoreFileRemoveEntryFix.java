/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 hsz Jakub Chrzanowski <jakub@hsz.mobi>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.intellij.openapi.vcs.changes.ignore.codeInspection;

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ignore.psi.IgnoreEntry;
import com.intellij.openapi.vcs.changes.ignore.psi.IgnoreTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * QuickFix action that removes specified entry handled by code inspections like
 * {@link IgnoreFileDuplicateEntryInspection}
 */
public class IgnoreFileRemoveEntryFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  /**
   * Builds a new instance of {@link IgnoreFileRemoveEntryFix}.
   *
   * @param entry an element that will be handled with QuickFix
   */
  public IgnoreFileRemoveEntryFix(@NotNull IgnoreEntry entry) {
    super(entry);
  }

  /**
   * Gets QuickFix name.
   *
   * @return QuickFix action name
   */
  @Override
  public @NotNull String getText() {
    return VcsBundle.message("ignore.quick.fix.remove.entry");
  }

  /**
   * Handles QuickFix action invoked on {@link IgnoreEntry}.
   *
   * @param project      the {@link Project} containing the working file
   * @param psiFile         the {@link PsiFile} containing handled entry
   * @param startElement the {@link IgnoreEntry} that will be removed
   * @param endElement   the {@link PsiElement} which is ignored in invoked action
   */
  @Override
  public void invoke(@NotNull Project project, @NotNull PsiFile psiFile,
                     @Nullable("is null when called from inspection") Editor editor,
                     @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    if (startElement instanceof IgnoreEntry) {
      removePreviousCrlfIfAny(startElement);
      startElement.delete();
    }
  }

  /**
   * Remove previous CRLF element if it exist.
   *
   * @param startElement working PSI element
   */
  private static void removePreviousCrlfIfAny(PsiElement startElement) {
    ASTNode node = TreeUtil.findSiblingBackward(startElement.getNode(), IgnoreTypes.CRLF);
    if (node != null) {
      node.getPsi().delete();
    }
  }

  /**
   * Gets QuickFix family name.
   *
   * @return QuickFix family name
   */
  @Override
  public @NotNull String getFamilyName() {
    return VcsBundle.message("ignore.codeInspection.group");
  }
}
