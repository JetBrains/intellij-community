// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.features

import com.intellij.ide.IdeBundle
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.fileTypes.PlainTextLikeFileType
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

@ApiStatus.Internal
class AnsiHighlighterDetector : FileHandlerFeatureDetector {
  private val ansiRegex: Regex = """
    \u001b\[[0-9;]*[mGKHFJ]|
    \u001b\([0-9;]*[BJ]|
    \u001b][0-9;]*.*\u0007|
    \u001b[PX^_].*\u001b\\
  """.trimIndent().toRegex()

  override val id: String = "ansi-highlighter"
  override val displayName: @Nls Supplier<String> = IdeBundle.messagePointer("feature.file.handler.ansi.highlighter")

  override fun isSupported(file: VirtualFile): Boolean {
    val extension = file.extension?.lowercase()
    if (extension != "txt" && extension != "log") return false

    val fileType = file.fileType
    if (fileType.isBinary) return false
    if (fileType !is PlainTextLikeFileType) return false

    val logString = LoadTextUtil.loadText(file, 1000)
    return ansiRegex.containsMatchIn(logString)
  }
}
