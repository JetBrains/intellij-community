// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight

import com.intellij.lang.Commenter

class EditorConfigCommenter : Commenter {
  override fun getLineCommentPrefix() = "# "
  override fun getBlockCommentPrefix() = ""
  override fun getBlockCommentSuffix(): String? = null
  override fun getCommentedBlockCommentPrefix(): String? = null
  override fun getCommentedBlockCommentSuffix(): String? = null
}
