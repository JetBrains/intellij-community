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

/**
 * TestDialogHandler is invoked by the {@link TestDialogManager} instead of showing a dialog on a screen
 * (which is usually impossible for tests).
 * It's purpose is to modify dialog fields and return the dialog exit code,
 * which will be available to the code which has invoked the dialog.
 *
 * @author Kirill Likhodedov
 */
public interface TestDialogHandler<T extends DialogWrapper> {

  /**
   * Do something with the dialog (modify its instance fields, for example)
   * and return the exit code - as if user pressed one of exit buttons.
   *
   * @param dialog dialog to be handled.
   * @return DialogWrapper exit code, for example, {@link DialogWrapper#OK_EXIT_CODE}.
   */
  int handleDialog(T dialog);

}
