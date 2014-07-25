/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.vcs.AbstractDataProviderPanel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * This viewer is shown, when the compared contents are equal or differ only in line separators.
 *
 * @author Irina.Chernushina
 * @author Kirill Likhodedov
 */
public class ErrorDiffViewer implements DiffViewer {
  @NotNull private final DiffRequest myRequest;

  protected ErrorDiffViewer(@NotNull DiffRequest request) {
    myRequest = request;
  }

  @Override
  public boolean canShowRequest(DiffRequest request) {
    return false;
  }

  @Override
  public void setDiffRequest(DiffRequest request) {
    throw new IllegalStateException();
  }

  @Override
  public JComponent getComponent() {
    final JPanel result = new AbstractDataProviderPanel(new BorderLayout(), false) {
      @Override
      public void calcData(DataKey key, DataSink sink) {
        final Object data = myRequest.getGenericData().get(key.getName());
        if (data != null) {
          sink.put(key, data);
        }
      }
    };

    final ActionManager actionManager = ActionManager.getInstance();
    final DiffToolbarComponent toolbarComponent = new DiffToolbarComponent(result);
    final DiffRequest.ToolbarAddons addons = new DiffRequest.ToolbarAddons() {
      @Override
      public void customize(DiffToolbar toolbar) {
        toolbar.addAction(actionManager.getAction("DiffPanel.Toolbar"));
        toolbar.addAction(actionManager.getAction("ContextHelp"));
        toolbar.addSeparator();
      }
    };
    toolbarComponent.resetToolbar(addons);
    final DiffToolbarImpl toolbar = toolbarComponent.getToolbar();
    myRequest.customizeToolbar(toolbar);
    /*group.addAction(actionManager.getAction("Diff.PrevChange"));
    group.addAction(actionManager.getAction("Diff.NextChange"));*/

    result.add(toolbarComponent, BorderLayout.NORTH);

    DiffContent content1 = myRequest.getContents()[0];
    DiffContent content2 = myRequest.getContents()[1];

    String message;
    if (DiffUtil.oneIsUnknown(content1, content2)) {
      message = DiffBundle.message("diff.can.not.show.unknown");
    }
    else {
      message = DiffBundle.message("diff.can.not.show");
    }

    final JPanel messagePanel = createMessagePanel(message);
    result.add(messagePanel, BorderLayout.CENTER);
    return result;
  }

  @NotNull
  private static JPanel createMessagePanel(@NotNull String message) {
    final JLabel label = new JLabel(message);
    label.setForeground(UIUtil.getInactiveTextColor());
    final JPanel wrapper = new JPanel(new GridBagLayout());
    wrapper.add(label,
                new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(1, 1, 1, 1), 0, 0));
    return wrapper;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @Override
  public int getContentsNumber() {
    return 0;
  }

  @Override
  public boolean acceptsType(DiffViewerType type) {
    return DiffViewerType.empty.equals(type);
  }
}
