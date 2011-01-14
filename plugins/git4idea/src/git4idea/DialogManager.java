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
package git4idea;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

/**
 * DialogManager is used to support showing dialogs in tests.
 * This class is used in production and it only calls {@link com.intellij.openapi.ui.DialogWrapper#show()} while tests use
 * {@link git4idea.tests.TestDialogManager} which instead of showing dialog (which is impossible on a headless environment) transfers
 * the control to the test.
 * @author Kirill Likhodedov
 */
public class DialogManager {

  public static DialogManager getInstance(Project project) {
    return ServiceManager.getService(project, DialogManager.class);
  }

  /**
   * Show the dialog. Just calls {@link DialogWrapper#show} but can be overridden to avoid showing it.
   */
  public void showDialog(DialogWrapper dialog) {
    dialog.show();
  }

}
