/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.status;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class InlineProgressIndicator extends ProgressIndicatorBase implements Disposable {

  private final TextPanel myText = new TextPanel();
  private final TextPanel myText2 = new TextPanel();
  private final JBIterable<ProgressButton> myEastButtons;

  protected JProgressBar myProgress;

  private JPanel myComponent;

  private final boolean myCompact;
  private TaskInfo myInfo;

  private final TextPanel myProcessName = new TextPanel();
  private boolean myDisposed;

  public InlineProgressIndicator(boolean compact, @NotNull TaskInfo processInfo) {
    myCompact = compact;
    myInfo = processInfo;

    myProgress = new JProgressBar(SwingConstants.HORIZONTAL);
    UIUtil.applyStyle(UIUtil.ComponentStyle.MINI, myProgress);

    myComponent = new MyComponent(compact, myProcessName);
    myEastButtons = createEastButtons();
    myEastButtons.forEach(b -> b.button.setOpaque(false));
    if (myCompact) {
      myComponent.setOpaque(false);
      myComponent.setLayout(new BorderLayout(2, 0));
      JPanel textAndProgress = new NonOpaquePanel(new BorderLayout());
      textAndProgress.add(myText, BorderLayout.CENTER);

      final NonOpaquePanel progressWrapper = new NonOpaquePanel(new BorderLayout());
      progressWrapper.setBorder(JBUI.Borders.empty(0, 4));
      progressWrapper.add(myProgress, BorderLayout.CENTER);

      textAndProgress.add(progressWrapper, BorderLayout.EAST);
      myComponent.add(textAndProgress, BorderLayout.CENTER);
      myComponent.add(createButtonPanel(myEastButtons.map(b -> b.button)), BorderLayout.EAST);
      myComponent.setToolTipText(processInfo.getTitle() + ". " + IdeBundle.message("progress.text.clickToViewProgressWindow"));
    } else {
      myComponent.setLayout(new BorderLayout());
      myProcessName.setText(processInfo.getTitle());
      myComponent.add(myProcessName, BorderLayout.NORTH);
      myProcessName.setForeground(UIUtil.getPanelBackground().brighter().brighter());
      myProcessName.setBorder(JBUI.Borders.empty(2));

      final NonOpaquePanel content = new NonOpaquePanel(new BorderLayout());
      content.setBorder(JBUI.Borders.empty(2, 2, 2, myInfo.isCancellable() ? 2 : 4));
      myComponent.add(content, BorderLayout.CENTER);

      content.add(createButtonPanel(myEastButtons.map(b -> withBorder(b.button))), BorderLayout.EAST);
      content.add(myText, BorderLayout.NORTH);
      content.add(myProgress, BorderLayout.CENTER);
      content.add(myText2, BorderLayout.SOUTH);

      myComponent.setBorder(JBUI.Borders.empty(2));
    }

    if (!myCompact) {
      myProcessName.recomputeSize();
      myText.recomputeSize();
      myText2.recomputeSize();
    }

  }

  static JPanel createButtonPanel(Iterable<JComponent> components) {
    JPanel iconsPanel = new NonOpaquePanel(new GridBagLayout());
    GridBag gb = new GridBag().setDefaultFill(GridBagConstraints.BOTH);
    for (JComponent component : components) {
      iconsPanel.add(component, gb.next());
    }
    return iconsPanel;
  }

  private static Wrapper withBorder(InplaceButton button) {
    Wrapper wrapper = new Wrapper(button);
    wrapper.setBorder(JBUI.Borders.empty(0, 3, 0, 2));
    return wrapper;
  }

  protected JBIterable<ProgressButton> createEastButtons() {
    return JBIterable.of(createCancelButton());
  }

  private ProgressButton createCancelButton() {
    InplaceButton cancelButton = new InplaceButton(
      new IconButton(myInfo.getCancelTooltipText(), 
                     myCompact ? AllIcons.Process.StopSmall : AllIcons.Process.Stop, 
                     myCompact ? AllIcons.Process.StopSmallHovered : AllIcons.Process.StopHovered),
      new ActionListener() {
        @Override
        public void actionPerformed(final ActionEvent e) {
          cancelRequest();
        }
      }).setFillBg(false);

    cancelButton.setVisible(myInfo.isCancellable());

    return new ProgressButton(cancelButton, () -> cancelButton.setPainting(!isStopping()));
  }

  protected void cancelRequest() {
    cancel();
  }

  protected void updateProgress() {
    queueProgressUpdate();
  }

  protected void updateAndRepaint() {
    if (isDisposed()) return;

    updateProgressNow();

    myComponent.repaint();
  }

  public void updateProgressNow() {
    if (isPaintingIndeterminate()) {
      myProgress.setIndeterminate(true);
    }
    else {
      myProgress.setIndeterminate(false);
      myProgress.setMinimum(0);
      myProgress.setMaximum(100);
    }
    if (getFraction() > 0) {
      myProgress.setValue((int)(getFraction() * 99 + 1));
    }

    myText.setText(getText() != null ? getText() : "");
    myText2.setText(getText2() != null ? getText2() : "");

    if (myCompact && StringUtil.isEmpty(myText.getText())) {
      myText.setText(myInfo.getTitle());
    }

    if (isStopping()) {
      if (myCompact) {
        myText.setText("Stopping - " + myText.getText());
      } else {
        myProcessName.setText("Stopping - " + myInfo.getTitle());
      }
      myText.setEnabled(false);
      myText2.setEnabled(false);
      myProgress.setEnabled(false);
    } else {
      myText.setEnabled(true);
      myText2.setEnabled(true);
      myProgress.setEnabled(true);
    }
    
    myEastButtons.forEach(b -> b.updateAction.run());
  }

  protected boolean isPaintingIndeterminate() {
    return isIndeterminate() || getFraction() == 0;
  }

  private boolean isStopping() {
    return wasStarted() && (isCanceled() || !isRunning()) && !isFinished();
  }

  protected boolean isFinished() {
    return false;
  }

  protected void queueProgressUpdate() {
    updateAndRepaint();
  }

  protected void queueRunningUpdate(@NotNull Runnable update) {
    update.run();
  }

  @Override
  protected void onProgressChange() {
    updateProgress();
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public boolean isCompact() {
    return myCompact;
  }

  public TaskInfo getInfo() {
    return myInfo;
  }

  private class MyComponent extends JPanel {
    private final boolean myCompact;
    private final JComponent myProcessName;

    private MyComponent(final boolean compact, final JComponent processName) {
      myCompact = compact;
      myProcessName = processName;
      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(final MouseEvent e) {
          if (UIUtil.isCloseClick(e) && getBounds().contains(e.getX(), e.getY())) {
            cancelRequest();
          }
        }
      });
    }

    @Override
    protected void paintComponent(final Graphics g) {
      if (myCompact) {
        super.paintComponent(g);
        return;
      }

      final GraphicsConfig c = GraphicsUtil.setupAAPainting(g);
      UISettings.setupAntialiasing(g);

      int arc = 8;
      Color bg = getBackground();
      final Rectangle bounds = myProcessName.getBounds();
      final Rectangle label = SwingUtilities.convertRectangle(myProcessName.getParent(), bounds, this);

      g.setColor(UIUtil.getPanelBackground());
      g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);

      if (!UIUtil.isUnderDarcula()) {
        bg = ColorUtil.toAlpha(bg.darker().darker(), 230);
        g.setColor(bg);

        g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);

        g.setColor(UIUtil.getPanelBackground());
        g.fillRoundRect(0, getHeight() / 2, getWidth() - 1, getHeight() / 2, arc, arc);
        g.fillRect(0, (int)label.getMaxY() + 1, getWidth() - 1, getHeight() / 2);
      } else {
        bg = bg.brighter();
        g.setColor(bg);
        g.drawLine(0, (int)label.getMaxY() + 1, getWidth() - 1, (int)label.getMaxY() + 1);
      }

      g.setColor(bg);
      g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);

      c.restore();
    }
  }

  @Override
  public void dispose() {
    if (myDisposed) return;

    myDisposed = true;

    myComponent.removeAll();

    myComponent = null;

    if (myProgress != null) {
      UIUtil.disposeProgress(myProgress);
    }
    myProgress = null;
    myInfo = null;
  }

  private boolean isDisposed() {
    return myDisposed;
  }
}

class ProgressButton {
  final InplaceButton button;
  final Runnable updateAction;

  ProgressButton(InplaceButton button, Runnable updateAction) {
    this.button = button;
    this.updateAction = updateAction;
  }
}