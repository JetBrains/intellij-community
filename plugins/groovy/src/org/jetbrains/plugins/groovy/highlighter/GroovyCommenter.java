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

package org.jetbrains.plugins.groovy.highlighter;

import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.psi.PsiComment;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;

/**
 * @author ilyas
 */

public class GroovyCommenter implements CodeDocumentationAwareCommenter {
  public String getLineCommentPrefix() {
    return "//";
  }

  public String getBlockCommentPrefix() {
    return "/*";
  }

  public String getBlockCommentSuffix() {
    return "*/";
  }

  public String getCommentedBlockCommentPrefix() {
    return null;
  }

  public String getCommentedBlockCommentSuffix() {
    return null;
  }

  @Nullable
  public IElementType getLineCommentTokenType() {
    return mSL_COMMENT;
  }

  @Nullable
  public IElementType getBlockCommentTokenType() {
    return mML_COMMENT;
  }

  @Nullable
  public IElementType getDocumentationCommentTokenType() {
    return GROOVY_DOC_COMMENT;
  }

  @Nullable
  public String getDocumentationCommentPrefix() {
    return "/**";
  }

  @Nullable
  public String getDocumentationCommentLinePrefix() {
    return "*";
  }

  @Nullable
  public String getDocumentationCommentSuffix() {
    return "*/";
  }

  public boolean isDocumentationComment(PsiComment element) {
    return element.getText().startsWith(getDocumentationCommentPrefix());
  }
}
