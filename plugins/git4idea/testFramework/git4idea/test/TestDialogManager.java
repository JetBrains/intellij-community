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
package git4idea.test;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.containers.ContainerUtil;
import git4idea.DialogManager;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * <p>TestDialogManager instead of showing the dialog, gives the control to a {@link git4idea.test.TestDialogHandler} which can specify the dialog exit code
 *    (thus simulation different user choices) or even change other elements in the dialog.</p>
 * <p>To use it a test should register the {@link TestDialogHandler} implementation. For example:
 * <pre><code>
 *     myDialogManager.registerDialogHandler(GitConvertFilesDialog.class, new TestDialogHandler<GitConvertFilesDialog>() {
 *       &#064;Override public int handleDialog(GitConvertFilesDialog dialog) {
 *         dialogShown.set(true);
 *         return GitConvertFilesDialog.OK_EXIT_CODE;
 *       }
 *     });
 * </code></pre>
 * <p>Only one TestDialogHandler can be registered per test for a certain DialogWrapper class.</p>
 * @see TestDialogHandler
 * @author Kirill Likhodedov
 */
public class TestDialogManager extends DialogManager {

  private final Map<Class, TestDialogHandler> myHandlers = ContainerUtil.newHashMap();

  @Override
  protected void showDialog(@NotNull DialogWrapper dialog) {
    TestDialogHandler handler = myHandlers.get(dialog.getClass());
    int exitCode = handler != null ? handler.handleDialog(dialog) : DialogWrapper.OK_EXIT_CODE;
    dialog.close(exitCode, exitCode == DialogWrapper.OK_EXIT_CODE);
  }

  public void registerDialogHandler(@NotNull Class<? extends DialogWrapper> dialogClass, @NotNull TestDialogHandler handler) {
    myHandlers.put(dialogClass, handler);
  }

  public void cleanup() {
    myHandlers.clear();
  }

}
