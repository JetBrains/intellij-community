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
package org.jetbrains.plugins.groovy.editor.selection;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl.GrDocCommentUtil;

import java.util.Collections;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GroovyMembersWithDocSelectioner extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof GrDocComment || e instanceof GrDocCommentOwner;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    final GrDocCommentOwner owner;
    final GrDocComment doc;
    if (e instanceof GrDocComment) {
      doc = (GrDocComment)e;
      owner = GrDocCommentUtil.findDocOwner(doc);
    }
    else {
      owner = (GrDocCommentOwner)e;
      doc = GrDocCommentUtil.findDocComment(owner);
    }

    if (doc == null || owner == null) return Collections.emptyList();

    final TextRange range = new TextRange(doc.getTextRange().getStartOffset(), owner.getTextRange().getEndOffset());
    return ExtendWordSelectionHandlerBase.expandToWholeLine(editorText, range, true);
  }
}
