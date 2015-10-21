/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.util.containers.ContainerUtil
import git4idea.DialogManager
import javax.swing.Icon

/**
 *
 * TestDialogManager instead of showing the dialog, gives the control to a [TestDialogHandler] which can specify the dialog exit code
 * (thus simulating different user choices) or even change other elements in the dialog.
 *
 * To use it a test should register the [TestDialogHandler] implementation. For example:
 * ```
 * myDialogManager.registerDialogHandler(GitConvertFilesDialog.class, new TestDialogHandler() {
 *   @Override public int handleDialog(GitConvertFilesDialog dialog) {
 *     dialogShown.set(true);
 *     return GitConvertFilesDialog.OK_EXIT_CODE;
 *   }
 * });
 * ```
 *
 * Only one TestDialogHandler can be registered per test for a certain DialogWrapper class.
 *
 * Apart from dialog, the TestDialogManager is capable to handle [Messages]. For this pass a function to the method [#]
 * @see TestDialogHandler
 */
public class TestDialogManager : DialogManager() {

  private val myHandlers = ContainerUtil.newHashMap<Class<out Any>, TestDialogHandler<DialogWrapper>>()
  private var myOnMessage: (String) -> Int = { throw IllegalStateException() }

  override fun showDialog(dialog: DialogWrapper) {
    val handler = myHandlers.get(dialog.javaClass)
    var exitCode = DialogWrapper.OK_EXIT_CODE
    try {
      if (handler != null) {
        exitCode = handler.handleDialog(dialog)
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

  public fun <T : DialogWrapper> registerDialogHandler(dialogClass: Class<T>, handler: TestDialogHandler<T>) {
    myHandlers.put(dialogClass, handler as TestDialogHandler<DialogWrapper>)
  }

  fun onMessage(handler: (String) -> Int) {
    myOnMessage = handler
  }

  @Deprecated("Use onMessage")
  public fun registerMessageHandler(handler: TestMessageHandler) {
    myOnMessage = { handler.handleMessage(it) }
  }

  public fun cleanup() {
    myHandlers.clear()
  }
}
