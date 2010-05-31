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
package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.LafManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.impl.content.GraphicsConfig;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.ui.UIUtil;

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

  private final FixedHeightLabel myProcessName = new FixedHeightLabel();
  private boolean myDisposed;

  private long myLastTimeProgressWasAtZero;
  private boolean myLastTimeProgressWasZero;

  public InlineProgressIndicator(boolean compact, TaskInfo processInfo) {
    myCompact = compact;
    myInfo = processInfo;

    myCancelButton = new InplaceButton(new IconButton(processInfo.getCancelTooltipText(),
                                                      IconLoader.getIcon("/process/stop.png"),
                                                      IconLoader.getIcon("/process/stopHovered.png")) {
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
      if (!myInfo.isCancellable()) progressWrapper.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
      final GridBagConstraints c = new GridBagConstraints();
      c.weightx = 1;
      c.weighty = 1;
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
      final Font font = myProcessName.getFont();

      final boolean aqua = LafManager.getInstance().isUnderAquaLookAndFeel();

      int size = font.getSize() - (aqua ? 4 : 2);
      if (size < (aqua ? 8 : 10)) {
        size = (aqua ? 8 : 10);
      }
      myProcessName.setFont(font.deriveFont(Font.PLAIN, size));
      myProcessName.setForeground(UIManager.getColor("Panel.background").brighter().brighter());
      myProcessName.setBorder(new EmptyBorder(2, 2, 2, 2));

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

      myComponent.setBorder(new EmptyBorder(2, 2, 2, 2));
      myProgress.setActive(false);
    }

    UIUtil.removeQuaquaVisualMarginsIn(myComponent);

    if (!myCompact) {
      myProcessName.recomputeSize();
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

  // TODO: use TextPanel instead
  private static class FixedHeightLabel extends JLabel {
    private Dimension myPrefSize;

    public FixedHeightLabel() {
      setOpaque(false);
      if (SystemInfo.isMac) setFont(UIUtil.getLabelFont().deriveFont(11.0f));
    }

    public void recomputeSize() {
      final String old = getText();
      setText("XXX");
      myPrefSize = getPreferredSize();
      setText(old);
    }


    public Dimension getPreferredSize() {
      final Dimension size = super.getPreferredSize();
      if (myPrefSize != null) {
        size.height = myPrefSize.height;
      }

      return size;
    }
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
    private final FixedHeightLabel myProcessName;

    private MyComponent(final boolean compact, final FixedHeightLabel processName) {
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

      final GraphicsConfig c = new GraphicsConfig(g);
      c.setAntialiasing(true);

      int arc = 8;

      g.setColor(UIManager.getColor("Panel.background"));
      g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
      
      Color bg = getBackground().darker().darker();
      bg = new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 230);

      g.setColor(bg);

      final Rectangle bounds = myProcessName.getBounds();
      final Rectangle label = SwingUtilities.convertRectangle(myProcessName.getParent(), bounds, this);


      g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);

      g.setColor(UIManager.getColor("Panel.background"));
      g.fillRoundRect(0, getHeight() / 2, getWidth() - 1, getHeight() / 2, arc, arc);
      g.fillRect(0, (int)label.getMaxY() + 1, getWidth() - 1, getHeight() / 2);

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
