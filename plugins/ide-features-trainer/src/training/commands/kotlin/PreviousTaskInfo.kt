// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.commands.kotlin

import com.intellij.openapi.editor.LogicalPosition
import training.learn.lesson.kimpl.LessonSample
import java.awt.Component

interface PreviousTaskInfo {
  val text: String
  val position: LogicalPosition
  val sample: LessonSample
  val ui: Component?
}