// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("TextContentTestUtil")

package com.intellij.grazie.text

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory

/**
 * Returns the text of [text] with `|` at each unknown offset.
 */
fun unknownOffsets(text: TextContent): String {
  val sb = StringBuilder(text)
  for (offset in text.unknownOffsets().reversedArray()) {
    sb.insert(offset, "|")
  }
  return sb.toString()
}

fun extractText(fileName: String, fileText: String, offset: Int, project: Project): TextContent? {
  return TextExtractor.findTextAt(createFile(fileName, fileText, project), offset, TextContent.TextDomain.ALL)
}

fun extractAllTexts(fileName: String, fileText: String, project: Project): Set<TextContent> {
  return TextExtractor.findAllTextContents(createFile(fileName, fileText, project).viewProvider, TextContent.TextDomain.ALL)
}

fun createFile(fileName: String, fileText: String, project: Project): PsiFile {
  val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)
  return PsiFileFactory.getInstance(project).createFileFromText(fileName, fileType, fileText)
}
