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
package git4idea.history.browser;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class SelfRemovingLabel {
  public static JPanel createComponent(final String text, final Icon icon, final JComponent parent, final Color textColor) {
    final JLabel textLabel = new JLabel(text);
    final JPanel panel = new JPanel(new BorderLayout());
    final JLabel label = new JLabel(icon);
    label.setOpaque(true);
    label.setBackground(textLabel.getBackground());
    textLabel.setOpaque(true);
    textLabel.setForeground(textColor);
    panel.add(label, BorderLayout.WEST);
    panel.add(textLabel, BorderLayout.EAST);
    //textLabel.setToolTipText(myDescription);
    //label.setToolTipText(myDescription);
    final Border originalBorder;
    if (SystemInfo.isMac) {
      originalBorder = BorderFactory.createLoweredBevelBorder();
    }
    else {
      originalBorder = textLabel.getBorder();
    }

    panel.setBorder(new CompoundBorder(IdeBorderFactory.createEmptyBorder(4, 0, 4, 0), originalBorder));

    textLabel.setOpaque(true);
    textLabel.setBorder(IdeBorderFactory.createEmptyBorder(0, 5, 0, 5));

    label.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        parent.remove(panel);
        parent.repaint();
      }
    });
    return panel;
  }
}
