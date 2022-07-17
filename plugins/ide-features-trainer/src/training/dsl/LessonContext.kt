// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.dsl

import com.intellij.openapi.application.ModalityState
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.Nls
import training.learn.course.KLesson

@LearningDsl
abstract class LessonContext : LearningDslBase {
  /**
   * Start a new task in a lesson context
   */
  @RequiresEdt
  open fun task(taskContent: TaskContext.() -> Unit) = Unit

  /**
   * There will not be any freeze in GUI thread.
   * The continuation of the script will be scheduled with the [delayMillis]
   */
  open fun waitBeforeContinue(delayMillis: Int) = Unit

  /// SHORTCUTS ///

  fun prepareRuntimeTask(modalityState: ModalityState? = ModalityState.any(), preparation: TaskRuntimeContext.() -> Unit) {
    task {
      addFutureStep {
        taskInvokeLater(modalityState) {
          preparation()
          completeStep()
        }
      }
    }
  }

  /** Describe a simple task: just one action required */
  fun actionTask(action: String, @Nls getText: TaskContext.(action: String) -> String) {
    task {
      text(getText(action))
      trigger(action)
      test { actions(action) }
    }
  }

  fun text(@Language("HTML") @Nls text: String) = task { text(text) }

  /**
   * Just shortcut to write action name once
   * @see task
   */
  fun task(action: String, taskContent: TaskContext.(action: String) -> Unit) = task {
    taskContent(action)
  }

  /** Select text in editor */
  fun select(startLine: Int, startColumn: Int, endLine: Int, endColumn: Int) = prepareRuntimeTask {
    select(startLine, startColumn, endLine, endColumn)
  }

  open fun caret(offset: Int) = prepareRuntimeTask {
    caret(offset)
  }

  /** NOTE:  [line] and [column] starts from 1 not from zero. So these parameters should be same as in editors. */
  open fun caret(line: Int, column: Int) = prepareRuntimeTask {
    caret(line, column)
  }

  open fun caret(text: String, select: Boolean = false) = prepareRuntimeTask {
    caret(text, select)
  }

  open fun caret(position: LessonSamplePosition) = prepareRuntimeTask {
    caret(position)
  }

  open fun prepareSample(sample: LessonSample, checkSdkConfiguration: Boolean = true) {
    prepareRuntimeTask { setSample(sample) }

    if (checkSdkConfiguration) {
      sdkConfigurationTasks()
    }
  }

  internal abstract val lesson: KLesson
}
