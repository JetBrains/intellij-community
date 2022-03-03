// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.light

import com.intellij.ide.lightEdit.LightEditTabAttributesProvider
import com.intellij.ide.lightEdit.LightEditorInfo
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Color
import java.awt.Font

internal class LightGitTabAttributesProvider : LightEditTabAttributesProvider, Disposable {
  override fun calcAttributes(editorInfo: LightEditorInfo): TextAttributes {
    return foreground(LightGitTracker.getInstance().getFileStatus(editorInfo.file).color)
  }

  override fun dispose() = Unit
}

private fun foreground(color: Color?) = TextAttributes(color, null, null, null, Font.PLAIN)