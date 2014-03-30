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
package com.intellij.openapi.wm.impl.status;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class InlineProgressIndicator extends ProgressIndicatorBase implements Disposable {

  private final TextPanel myText = new TextPanel();
  private final TextPanel myText2 = new TextPanel();

  private MyProgressBar myProgress;

  private JPanel myComponent;

  private final InplaceButton myCancelButton;

  private final boolean myCompact;
  private TaskInfo myInfo;

  private final TextPanel myProcessName = new TextPanel();
  private boolean myDisposed;

  private long myLastTimeProgressWasAtZero;
  private boolean myLastTimeProgressWasZero;

  public InlineProgressIndicator(boolean compact, @NotNull TaskInfo processInfo) {
    myCompact = compact;
    myInfo = processInfo;

    myCancelButton = new InplaceButton(new IconButton(processInfo.getCancelTooltipText(),
                                                      AllIcons.Process.Stop,
                                                      AllIcons.Process.StopHovered) {
    }, new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        cancelRequest();
      }
    }).setFillBg(true);

    myCancelButton.setVisible(myInfo.isCancellable());
    myCancelButton.setOpaque(false);
    myCancelButton.setToolTipText(processInfo.getCancelTooltipText());
    myCancelButton.setFillBg(false);

    myProgress = new MyProgressBar(JProgressBar.HORIZONTAL, compact);

    myComponent = new MyComponent(compact, myProcessName);
    if (myCompact) {
      myComponent.setOpaque(false);
      myComponent.setLayout(new BorderLayout(2, 0));
      final JPanel textAndProgress = new JPanel(new BorderLayout());
      textAndProgress.setOpaque(false);
      textAndProgress.add(myText, BorderLayout.CENTER);

      final NonOpaquePanel progressWrapper = new NonOpaquePanel(new GridBagLayout());
      progressWrapper.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
      final GridBagConstraints c = new GridBagConstraints();
      c.weightx = 1;
      c.weighty = 1;
      c.insets = new Insets(SystemInfo.isMacOSLion ? 1 : 0, 0, 1, myInfo.isCancellable() ? 0 : 4);
      c.fill = GridBagConstraints.HORIZONTAL;
      progressWrapper.add(myProgress, c);

      textAndProgress.add(progressWrapper, BorderLayout.EAST);
      myComponent.add(textAndProgress, BorderLayout.CENTER);
      myComponent.add(myCancelButton, BorderLayout.EAST);
      myComponent.setToolTipText(processInfo.getTitle() + ". " + IdeBundle.message("progress.text.clickToViewProgressWindow"));
      myProgress.setActive(false);
    } else {
      myComponent.setLayout(new BorderLayout());
      myProcessName.setText(processInfo.getTitle());
      myComponent.add(myProcessName, BorderLayout.NORTH);
      myProcessName.setForeground(UIUtil.getPanelBackground().brighter().brighter());
      myProcessName.setBorder(new EmptyBorder(2, 2, 2, 2));
      myProcessName.setDecorate(false);

      final NonOpaquePanel content = new NonOpaquePanel(new BorderLayout());
      content.setBorder(new EmptyBorder(2, 2, 2, myInfo.isCancellable() ? 2 : 4));
      myComponent.add(content, BorderLayout.CENTER);

      final Wrapper cancelWrapper = new Wrapper(myCancelButton);
      cancelWrapper.setOpaque(false);
      cancelWrapper.setBorder(new EmptyBorder(0, 3, 0, 2));

      content.add(cancelWrapper, BorderLayout.EAST);
      content.add(myText, BorderLayout.NORTH);
      content.add(myProgress, BorderLayout.CENTER);
      content.add(myText2, BorderLayout.SOUTH);

      myText.setDecorate(false);
      myText2.setDecorate(false);

      myComponent.setBorder(new EmptyBorder(2, 2, 2, 2));
      myProgress.setActive(false);
    }

    if (!myCompact) {
      myProcessName.recomputeSize();
      myText.recomputeSize();
      myText2.recomputeSize();
    }

  }

  protected void cancelRequest() {
    cancel();
  }

  private void updateRunning() {
    queueRunningUpdate(EmptyRunnable.getInstance());
  }

  protected void updateProgress() {
    queueProgressUpdate(new Runnable() {
      public void run() {
        if (isDisposed()) return;

        updateProgressNow();

        myComponent.repaint();
      }
    });
  }

  public void updateProgressNow() {
    if (myLastTimeProgressWasAtZero == 0 && getFraction() == 0) {
      myLastTimeProgressWasAtZero = System.currentTimeMillis();
    }

    final long delta = System.currentTimeMillis() - myLastTimeProgressWasAtZero;
    boolean forcedIndeterminite = false;

    boolean indeterminate = isIndeterminate();
    if (!indeterminate && getFraction() == 0) {
      if (delta > 2000 && !myCompact) {
          indeterminate = true;
          forcedIndeterminite = true;
        } else {
          forcedIndeterminite = false;
        }
    }

    final boolean visible = getFraction() > 0 || (indeterminate || forcedIndeterminite);
    updateVisibility(myProgress, visible);
    if (indeterminate || forcedIndeterminite) {
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

    if (myCompact && myText.getText().length() == 0) {
      myText.setText(myInfo.getTitle());
    }

    myCancelButton.setPainting(isCancelable());

    if (getFraction() == 0) {
      if (!myLastTimeProgressWasZero) {
        myLastTimeProgressWasAtZero = System.currentTimeMillis();
        myLastTimeProgressWasZero = true;
      }
    } else {
      myLastTimeProgressWasZero = false;
    }

    final boolean isStopping = wasStarted() && (isCanceled() || !isRunning()) && !isFinished();
    if (isStopping) {
      if (myCompact) {
        myText.setText("Stopping - " + myText.getText());
      } else {
        myProcessName.setText("Stopping - " + myInfo.getTitle());
      }
      myText.setEnabled(false);
      myText2.setEnabled(false);
      myProgress.setEnabled(false);

      myCancelButton.setPainting(false);
    } else {
      myText.setEnabled(true);
      myText2.setEnabled(true);
      myProgress.setEnabled(true);
      myCancelButton.setPainting(true);
    }
  }

  protected boolean isFinished() {
    return false;
  }

  protected void queueProgressUpdate(Runnable update) {
    update.run();
  }

  protected void queueRunningUpdate(Runnable update) {
    update.run();
  }


  private void updateVisibility(MyProgressBar bar, boolean holdsValue) {
    if (holdsValue && !bar.isActive()) {
      bar.setActive(true);
      bar.revalidate();
      bar.repaint();
      myComponent.revalidate();
      myComponent.repaint();
    }
    else if (!holdsValue && bar.isActive()) {
      bar.setActive(false);
      bar.revalidate();
      bar.repaint();
      myComponent.revalidate();
      myComponent.repaint();
    }
  }

  protected void onProgressChange() {
    updateProgress();
  }

  protected void onRunningChange() {
    updateRunning();
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

  private static class MyProgressBar extends JProgressBar {
    private boolean myActive = true;
    private final boolean myCompact;

    public MyProgressBar(final int orient, boolean compact) {
      super(orient);
      myCompact = compact;
      putClientProperty("JComponent.sizeVariant", "mini");
    }


    public void paint(final Graphics g) {
      if (!myActive) return;
      super.paint(g);
    }

    @Override
    public void setIndeterminate(boolean newValue) {
      super.setIndeterminate(newValue);
      if (myCompact) {
        setVisible(!newValue);
      }
    }

    public boolean isActive() {
      return myActive;
    }


    public Dimension getPreferredSize() {
      if (!myActive && myCompact) return new Dimension(0, 0);
      return super.getPreferredSize();
    }

    public void setActive(final boolean active) {
      myActive = active;
    }
  }

  private class MyComponent extends JPanel {
    private final boolean myCompact;
    private final JComponent myProcessName;

    private MyComponent(final boolean compact, final JComponent processName) {
      myCompact = compact;
      myProcessName = processName;
      addMouseListener(new MouseAdapter() {
        public void mousePressed(final MouseEvent e) {
          if (UIUtil.isCloseClick(e) && getBounds().contains(e.getX(), e.getY())) {
            cancelRequest();
          }
        }
      });
    }

    protected void paintComponent(final Graphics g) {
      if (myCompact) {
        super.paintComponent(g);
        return;
      }

      final GraphicsConfig c = GraphicsUtil.setupAAPainting(g);
      GraphicsUtil.setupAntialiasing(g, true, true);

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
