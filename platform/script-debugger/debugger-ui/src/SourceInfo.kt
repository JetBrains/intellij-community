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
package org.jetbrains.debugger

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Url
import com.intellij.xdebugger.XSourcePosition

class SourceInfo @JvmOverloads constructor(private val file: VirtualFile, private val line: Int, val column: Int = -1, private var offset: Int = -1, val functionName: String? = null, val url: Url? = null) : XSourcePosition {
  override fun getFile(): VirtualFile = file

  override fun getLine(): Int = line

  override fun getOffset(): Int {
    if (offset == -1) {
      val document = runReadAction { if (file.isValid) FileDocumentManager.getInstance().getDocument(file) else null } ?: return -1
      offset = if (line < document.lineCount) document.getLineStartOffset(line) else -1
    }
    return offset
  }

  override fun createNavigatable(project: Project): OpenFileDescriptor = OpenFileDescriptor(project, file, line, column)

  override fun toString(): String = file.path + ":" + line + if (column == -1) "" else ":" + column
}