// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.dsl

import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Component

interface PreviousTaskInfo {
  val text: String
  val position: LogicalPosition
  val sample: LessonSample
  val ui: Component?
  val file: VirtualFile?
}