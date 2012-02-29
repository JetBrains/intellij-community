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
package git4idea.tests;

import com.intellij.openapi.ui.DialogWrapper;

import java.util.HashMap;
import java.util.Map;

/**
 * TestDialogManager instead of showing the dialog, gives the control to a {@link TestDialogHandler} which can specify the dialog exit code
 * (thus simulation different user choices) or even change other elements in the dialog.
 * To use it a test should:
 * <ol>
 * <li>register {@link TestDialogManager} as the {@link DialogManager} implementation:
 * <pre><code>
 *     String key = "git4idea.DialogManager";
 *     MutablePicoContainer picoContainer = (MutablePicoContainer) myProject.getPicoContainer();
 *     picoContainer.unregisterComponent(key);
 *     picoContainer.registerComponentImplementation(key, TestDialogManager.class);
 *     TestDialogManager dialogManager = (TestDialogManager)DialogManager.getInstance(myProject);
 * </code></pre></li>
 *
 * <li>register its {@link TestDialogHandler}:
 * <pre><code>
 *     myDialogManager.registerDialogHandler(GitConvertFilesDialog.class, new TestDialogHandler<GitConvertFilesDialog>() {
 *       &#064;Override public int handleDialog(GitConvertFilesDialog dialog) {
 *         dialogShown.set(true);
 *         return GitConvertFilesDialog.OK_EXIT_CODE;
 *       }
 *     });
 * </code></pre></li></ol>
 * @see TestDialogHandler
 * @author Kirill Likhodedov
 */
public class TestDialogManager {

  private Map<Class, TestDialogHandler> myHandlers = new HashMap<Class, TestDialogHandler>();

  public void show(DialogWrapper dialog) {
    final TestDialogHandler handler = myHandlers.get(dialog.getClass());
    int exitCode = DialogWrapper.OK_EXIT_CODE;
    if (handler != null) {
      exitCode = handler.handleDialog(dialog);
    }
    dialog.close(exitCode);
  }

  /**
   * Registers the dialog handler. Note that a test may register only one handler for one dialog type.
   * For different dialog types it may register different handlers.
   * @param dialogClass class of the dialog which will be handled instead of showing (dialog itself have to support this in its show() method).
   * @param handler     handler which will be invoked when dialog is about to show.
   */
  public void registerDialogHandler(Class dialogClass, TestDialogHandler handler) {
    myHandlers.put(dialogClass, handler);
  }
}
