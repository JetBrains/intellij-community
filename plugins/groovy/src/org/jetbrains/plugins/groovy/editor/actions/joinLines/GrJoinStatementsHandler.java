/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.editor.actions.joinLines;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

/**
 * @author Max Medvedev
 */
public class GrJoinStatementsHandler extends GrJoinLinesHandlerBase {
  @Override
  public int tryJoinStatements(@NotNull GrStatement first, @NotNull GrStatement second) {
    final PsiElement semi = PsiImplUtil.findTailingSemicolon(first);

    final Document document = PsiDocumentManager.getInstance(first.getProject()).getDocument(first.getContainingFile());
    if (document == null) return CANNOT_JOIN;
    final Integer endOffset = second.getTextRange().getStartOffset();
    if (semi != null) {
      final Integer offset = semi.getTextRange().getEndOffset();
      document.replaceString(offset, endOffset, " ");
      return offset + 1;
    }
    else {
      final Integer offset = first.getTextRange().getEndOffset();
      document.replaceString(offset, endOffset, "; ");
      return offset + 2;
    }
  }
}
