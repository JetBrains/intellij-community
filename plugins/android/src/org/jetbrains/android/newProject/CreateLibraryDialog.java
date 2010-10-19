/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.newProject;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.android.util.AndroidBundle;

import javax.swing.*;
import java.awt.event.KeyEvent;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 13, 2009
 * Time: 4:21:39 PM
 * To change this template use File | Settings | File Templates.
 */
public class CreateLibraryDialog extends DialogWrapper {
  private JPanel myPanel;
  private JRadioButton myProjectLibButton;
  private JRadioButton myGlobalLibButton;

  public CreateLibraryDialog(@NotNull Project project, @NotNull String libraryName) {
    super(project, true);
    setModal(true);
    setTitle(AndroidBundle.message("create.library.dialog.title"));
    myProjectLibButton.setSelected(true);
    myProjectLibButton.setMnemonic(KeyEvent.VK_P);
    myGlobalLibButton.setMnemonic(KeyEvent.VK_G);

    myProjectLibButton.setText(AndroidBundle.message("create.project.library", libraryName));
    myGlobalLibButton.setText(AndroidBundle.message("create.global.library", libraryName));
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public boolean isGlobalSelected() {
    return myGlobalLibButton.isSelected();
  }
}
