/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class CheckRemoteStatusCheckinHandlerFactory extends CheckinHandlerFactory {
  @NotNull
  @Override
  public CheckinHandler createHandler(final CheckinProjectPanel panel) {
    return new MyCheckinHandler(panel);
  }

  private static class MyCheckinHandler extends CheckinHandler {
    private final CheckinProjectPanel myPanel;
    private final VcsConfiguration myVcsConfiguration;

    private MyCheckinHandler(final CheckinProjectPanel panel) {
      myPanel = panel;
      myVcsConfiguration = VcsConfiguration.getInstance(myPanel.getProject());
    }

    @Override
    public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
      final JCheckBox checkUpToDate = new JCheckBox(VcsBundle.message("checkbox.checkin.options.check.files.up.to.date"));

      return new RefreshableOnComponent() {
        public JComponent getComponent() {
          final JPanel panel = new JPanel(new BorderLayout());
          panel.add(checkUpToDate, BorderLayout.WEST);
          return panel;
        }

        public void refresh() {
        }

        public void saveState() {
          myVcsConfiguration.CHECK_FILES_UP_TO_DATE_BEFORE_COMMIT = checkUpToDate.isSelected();
        }

        public void restoreState() {
          checkUpToDate.setSelected(myVcsConfiguration.CHECK_FILES_UP_TO_DATE_BEFORE_COMMIT);
        }
      };
    }

    @Override
    public void includedChangesChanged() {
      // todo recalculate message
    }

    private void doCheck() {
      myPanel.getFiles();
    }
  }
}
