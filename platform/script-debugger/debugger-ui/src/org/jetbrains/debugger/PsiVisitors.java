/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.debugger;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ForeignLeafPsiElement;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.util.DocumentUtil;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;

public final class PsiVisitors {
  public static <RESULT> RESULT visit(@NotNull XSourcePosition position, @NotNull Project project, @NotNull Visitor<RESULT> visitor) {
    return visit(position, project, visitor, null);
  }

  /**
   * Read action will be taken automatically
   */
  public static <RESULT> RESULT visit(@NotNull XSourcePosition position, @NotNull Project project, @NotNull Visitor<RESULT> visitor, RESULT defaultResult) {
    AccessToken token = ReadAction.start();
    try {
      Document document = FileDocumentManager.getInstance().getDocument(position.getFile());
      PsiFile file = document == null || document.getTextLength() == 0 ? null : PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (file == null) {
        return defaultResult;
      }

      int positionOffset;
      int column = position instanceof SourceInfo ? Math.max(((SourceInfo)position).getColumn(), 0) : 0;
      try {
        positionOffset = column == 0 ? DocumentUtil.getFirstNonSpaceCharOffset(document, position.getLine()) : document.getLineStartOffset(position.getLine()) + column;
      }
      catch (IndexOutOfBoundsException ignored) {
        return defaultResult;
      }

      PsiElement element = file.findElementAt(positionOffset);
      return element == null ? defaultResult : visitor.visit(position, element, positionOffset, document);
    }
    finally {
      token.finish();
    }
  }

  public interface Visitor<RESULT> {
    RESULT visit(@NotNull XSourcePosition position, @NotNull PsiElement element, int positionOffset, @NotNull Document document);
  }

  public static abstract class FilteringPsiRecursiveElementWalkingVisitor extends PsiRecursiveElementWalkingVisitor {
    @Override
    public void visitElement(PsiElement element) {
      if (!(element instanceof ForeignLeafPsiElement) && element.isPhysical()) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitWhiteSpace(PsiWhiteSpace space) {
    }

    @Override
    public void visitComment(PsiComment comment) {
    }

    @Override
    public void visitOuterLanguageElement(OuterLanguageElement element) {
    }
  }
}