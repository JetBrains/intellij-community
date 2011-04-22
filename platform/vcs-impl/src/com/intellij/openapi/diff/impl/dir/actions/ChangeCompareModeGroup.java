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
package com.intellij.openapi.diff.impl.dir.actions;

import com.intellij.ide.diff.DirDiffSettings;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;

/**
 * @author Konstantin Bulenkov
 */
public class ChangeCompareModeGroup extends ComboBoxAction {
  private final DefaultActionGroup myGroup;

  public ChangeCompareModeGroup(DirDiffTableModel model) {
    getTemplatePresentation().setText("Compare by");
    if (model.getSettings().showCompareModes) {
      final ArrayList<ChangeCompareModeAction> actions = new ArrayList<ChangeCompareModeAction>();
      for (DirDiffSettings.CompareMode mode : DirDiffSettings.CompareMode.values()) {
        actions.add(new ChangeCompareModeAction(model, mode));
      }
      myGroup = new DefaultActionGroup(actions.toArray(new ChangeCompareModeAction[actions.size()]));
    } else {
      getTemplatePresentation().setEnabled(false);
      getTemplatePresentation().setVisible(false);
      myGroup = new DefaultActionGroup();
    }
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    return myGroup;
  }
}
