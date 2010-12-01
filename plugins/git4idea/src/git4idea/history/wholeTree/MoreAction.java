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
package git4idea.history.wholeTree;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author irengrig
 */
public abstract class MoreAction  extends AnAction implements CustomComponentAction {
  protected final JLabel myLabel;
  private final JPanel myPanel;
  private boolean myEnabled;
  private boolean myVisible;

  protected MoreAction() {
    myPanel = new JPanel();
    final BoxLayout layout = new BoxLayout(myPanel, BoxLayout.X_AXIS);
    myPanel.setLayout(layout);
    myLabel = new JLabel("Load more");
    //myLabel.setFont(myLabel.getFont().deriveFont(Font.ITALIC));
    myLabel.setForeground(UIUtil.getTextFieldForeground());
    myLabel.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 1));
    myPanel.add(myLabel);
    myPanel.setBorder(new CompoundBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2), BorderFactory.createEtchedBorder()));
    final MouseAdapter mouseAdapter = new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (myEnabled) {
          actionPerformed(null);
        }
      }
    };
    myPanel.addMouseListener(mouseAdapter);
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    return myPanel;
  }

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
    myLabel.setText(myEnabled ? "Load more" : "Loading...");
    myLabel.setForeground(myEnabled ? UIUtil.getTextFieldForeground() : UIUtil.getInactiveTextColor());
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(myEnabled);
    e.getPresentation().setVisible(myVisible);
  }

  public void setVisible(boolean b) {
    myVisible = b;
  }
}
