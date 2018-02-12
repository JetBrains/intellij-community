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
package git4idea.test

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import git4idea.DialogManager
import javax.swing.Icon
import kotlin.test.assertNull

/**
 *
 * TestDialogManager instead of showing the dialog, gives the control to a [TestDialogHandler] or a function,
 * which can specify the dialog exit code (thus simulating different user choices) or even change other elements in the dialog.
 *
 * Example:
 * ```
 * myDialogManager.onDialog(GitConvertFilesDialog::class.java, {
 *   dialogShown = true
 *   return GitConvertFilesDialog.OK_EXIT_CODE
 * }
 * ```
 *
 * Only one TestDialogHandler or function can be registered per test for a certain DialogWrapper class.
 *
 * Apart from dialogs, the TestDialogManager is capable to handle [Messages]. For this pass a function to the method [#onMessage]
 */
class TestDialogManager : DialogManager() {

  private val DEFAULT_MESSAGE_HANDLER : (String) -> Int = { throw IllegalStateException("Message is not expected: $it") }

  private val myHandlers = hashMapOf<Class<out DialogWrapper>, (DialogWrapper) -> Int>()
  private var myOnMessage: (String) -> Int = DEFAULT_MESSAGE_HANDLER

  override fun showDialog(dialog: DialogWrapper) {
    var exitCode = DialogWrapper.OK_EXIT_CODE
    try {
      val handler = myHandlers[dialog.javaClass]
      if (handler != null) {
        exitCode = handler(dialog)
      }
      else {
        throw IllegalStateException("The dialog is not expected here: " + dialog.javaClass)
      }
    }
    finally {
      dialog.close(exitCode, exitCode == DialogWrapper.OK_EXIT_CODE)
    }
  }

  override fun showMessageDialog(project: Project, message: String, title: String, options: Array<String>,
                                 defaultButtonIndex: Int, icon: Icon?): Int {
    return myOnMessage.invoke(message)
  }

  override fun showMessageDialog(description: String, title: String, options: Array<String>, defaultButtonIndex: Int,
                                 focusedButtonIndex: Int, icon: Icon?, dontAskOption: DialogWrapper.DoNotAskOption?): Int {
    return myOnMessage.invoke(description)
  }

  fun <T : DialogWrapper> registerDialogHandler(dialogClass: Class<T>, handler: TestDialogHandler<T>) {
    onDialog(dialogClass, { handler.handleDialog(it) })
  }

  fun <T : DialogWrapper> onDialog(dialogClass: Class<T>, handler: (T) -> Int) {
    assertNull(myHandlers.put(dialogClass, handler as (DialogWrapper) -> Int))
  }

  fun onMessage(handler: (String) -> Int) {
    myOnMessage = handler
  }

  fun cleanup() {
    myHandlers.clear()
    myOnMessage = DEFAULT_MESSAGE_HANDLER
  }
}
