/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.editor.actions.joinLines

import com.intellij.psi.PsiDocumentManager
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil

/**
 * @author Max Medvedev
 */
class GrJoinStatementsHandler extends GrJoinLinesHandlerBase {
  @Override
  int tryJoinStatements(@NotNull GrStatement first, @NotNull GrStatement second) {
    final semi = PsiImplUtil.findTailingSemicolon(first)

    final document = PsiDocumentManager.getInstance(first.project).getDocument(first.containingFile)
    final endOffset = second.textRange.startOffset
    if (semi != null) {
      final offset = semi.textRange.endOffset
      document.replaceString(offset, endOffset, ' ')
      return offset + 1
    }
    else {
      final offset = first.textRange.endOffset
      document.replaceString(offset, endOffset, '; ')
      return offset + 2
    }
  }
}
