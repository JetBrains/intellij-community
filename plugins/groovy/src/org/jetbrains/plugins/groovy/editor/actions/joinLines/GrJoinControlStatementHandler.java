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

import com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;

public class GrJoinControlStatementHandler implements JoinLinesHandlerDelegate {
  @Override
  public int tryJoinLines(@NotNull Document document, @NotNull PsiFile file, int start, int end) {
    if (!(file instanceof GroovyFileBase)) return CANNOT_JOIN;

    final PsiElement startElement = file.findElementAt(start);
    if (startElement == null || !startElement.getNode().getElementType().equals(GroovyTokenTypes.mRPAREN)) return CANNOT_JOIN;

    final PsiElement parent = startElement.getParent();
    if (!(parent instanceof GrIfStatement || parent instanceof GrWhileStatement || parent instanceof GrForStatement)) return CANNOT_JOIN;

    GrStatement inner;
    if (parent instanceof GrIfStatement) {
      inner = ((GrIfStatement)parent).getThenBranch();
    }
    else if (parent instanceof GrWhileStatement) {
      inner = ((GrWhileStatement)parent).getBody();
    }
    else /*if (parent instanceof GrForStatement)*/ {
      inner = ((GrForStatement)parent).getBody();
    }


    if (inner instanceof GrBlockStatement) return CANNOT_JOIN;

    document.replaceString(start + 1, end, " ");
    return start + 2;
  }
}
