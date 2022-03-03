// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class InlineProgressIndicator extends ProgressIndicatorBase implements Disposable {
  protected final TextPanel myText;
  protected final TextPanel myText2;
  @NotNull
  private final JBIterable<ProgressButton> myEastButtons;

  @NotNull
  protected final JProgressBar myProgress;

  protected final JPanel myComponent;

  private final boolean myCompact;
  private TaskInfo myInfo;

  private final TextPanel myProcessName;
  private boolean myDisposed;

  public InlineProgressIndicator(boolean compact, @NotNull TaskInfo processInfo) {
    myCompact = compact;
    myInfo = processInfo;

    myProgress = new JProgressBar(SwingConstants.HORIZONTAL);
    UIUtil.applyStyle(UIUtil.ComponentStyle.MINI, myProgress);

    myText = new TextPanel();
    myText2 = new TextPanel();
    myProcessName = new TextPanel();
    myEastButtons = createEastButtons();
    myComponent = createComponent();
  }

  @NotNull
  protected JPanel createComponent() {
    MyComponent component = new MyComponent(myCompact, myProcessName);
    if (myCompact) {
      component.setLayout(new BorderLayout(2, 0));
      createCompactTextAndProgress(component);
      component.add(createButtonPanel(myEastButtons.map(b -> b.button)), BorderLayout.EAST);
      component.setToolTipText(myInfo.getTitle() + ". " + IdeBundle.message("progress.text.clickToViewProgressWindow"));
    }
    else {
      component.setLayout(new BorderLayout());
      myProcessName.setText(myInfo.getTitle());
      component.add(myProcessName, BorderLayout.NORTH);
      myProcessName.setForeground(UIUtil.getPanelBackground().brighter().brighter());
      myProcessName.setBorder(JBUI.Borders.empty(2));

      final NonOpaquePanel content = new NonOpaquePanel(new BorderLayout());
      content.setBorder(JBUI.Borders.empty(2, 2, 2, myInfo.isCancellable() ? 2 : 4));
      component.add(content, BorderLayout.CENTER);

      content.add(createButtonPanel(myEastButtons.map(b -> withBorder(b.button))), BorderLayout.EAST);
      content.add(myText, BorderLayout.NORTH);
      content.add(myProgress, BorderLayout.CENTER);
      content.add(myText2, BorderLayout.SOUTH);

      component.setBorder(JBUI.Borders.empty(2));
    }
    UIUtil.uiTraverser(component).forEach(o -> ((JComponent)o).setOpaque(false));

    if (!myCompact) {
      myProcessName.recomputeSize();
      myText.recomputeSize();
      myText2.recomputeSize();
    }
    return component;
  }

  protected void createCompactTextAndProgress(@NotNull JPanel component) {
    JPanel textAndProgress = new NonOpaquePanel(new BorderLayout());
    textAndProgress.add(myText, BorderLayout.CENTER);

    final NonOpaquePanel progressWrapper = new NonOpaquePanel(new BorderLayout());
    progressWrapper.setBorder(JBUI.Borders.empty(0, 4));
    progressWrapper.add(myProgress, BorderLayout.CENTER);

    textAndProgress.add(progressWrapper, BorderLayout.EAST);
    component.add(textAndProgress, BorderLayout.CENTER);
  }

  static JPanel createButtonPanel(Iterable<? extends JComponent> components) {
    JPanel iconsPanel = new NonOpaquePanel(new GridBagLayout());
    GridBag gb = new GridBag().setDefaultFill(GridBagConstraints.BOTH);
    for (JComponent component : components) {
      iconsPanel.add(component, gb.next());
    }
    return iconsPanel;
  }

  @NotNull
  private static Wrapper withBorder(@NotNull InplaceButton button) {
    Wrapper wrapper = new Wrapper(button);
    wrapper.setBorder(JBUI.Borders.empty(0, 3, 0, 2));
    return wrapper;
  }

  @NotNull
  protected JBIterable<ProgressButton> createEastButtons() {
    return JBIterable.of(createCancelButton());
  }

  @NotNull
  protected final ProgressButton createCancelButton() {
    InplaceButton cancelButton = new InplaceButton(
      new IconButton(myInfo.getCancelTooltipText(),
                     myCompact ? AllIcons.Process.StopSmall : AllIcons.Process.Stop,
                     myCompact ? AllIcons.Process.StopSmallHovered : AllIcons.Process.StopHovered),
      __ -> cancelRequest()).setFillBg(false);

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

    String text = getText();
    String text2 = getText2();
    setTextValue(text != null ? text : "");
    setText2Value(text2 != null ? text2 : "");

    if (myCompact && StringUtil.isEmpty(getTextValue())) {
      setTextValue(myInfo.getTitle());
    }

    if (isStopping()) {
      if (myCompact) {
        setTextValue(IdeBundle.message("progress.text.stopping", getTextValue()));
      }
      else {
        setProcessNameValue(IdeBundle.message("progress.text.stopping", myInfo.getTitle()));
        setTextEnabled(false);
        setText2Enabled(false);
      }
      myProgress.setEnabled(false);
    }
    else {
      setTextEnabled(true);
      setText2Enabled(true);
      myProgress.setEnabled(true);
    }

    myEastButtons.forEach(b -> b.updateAction.run());
  }

  protected @Nullable String getTextValue() {
    return myText.getText();
  }

  protected void setTextValue(@NlsContexts.ProgressText @NotNull String text) {
    myText.setText(text);
  }

  protected void setTextEnabled(boolean value) {
    myText.setEnabled(value);
  }

  protected @Nullable String getText2Value() {
    return myText2.getText();
  }

  protected void setText2Value(@NlsContexts.ProgressDetails @NotNull String text) {
    myText2.setText(text);
  }

  protected void setText2Enabled(boolean value) {
    myText2.setEnabled(value);
  }

  protected void setProcessNameValue(@NlsContexts.ProgressTitle @NotNull String text) {
    myProcessName.setText(text);
  }

  protected boolean isPaintingIndeterminate() {
    return isIndeterminate() || getFraction() == 0;
  }

  protected boolean isStopping() {
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

  @NotNull
  public JComponent getComponent() {
    return myComponent;
  }

  boolean isCompact() {
    return myCompact;
  }

  TaskInfo getInfo() {
    return myInfo;
  }

  private final class MyComponent extends JPanel {
    private final boolean myCompact;
    private final JComponent myProcessName;

    private MyComponent(final boolean compact, @NotNull JComponent processName) {
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

      if (!StartupUiUtil.isUnderDarcula()) {
        bg = ColorUtil.toAlpha(bg.darker().darker(), 230);
        g.setColor(bg);

        g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);

        g.setColor(UIUtil.getPanelBackground());
        g.fillRoundRect(0, getHeight() / 2, getWidth() - 1, getHeight() / 2, arc, arc);
        g.fillRect(0, (int)label.getMaxY() + 1, getWidth() - 1, getHeight() / 2);
      }
      else {
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

    UIUtil.disposeProgress(myProgress);
    GuiUtils.removePotentiallyLeakingReferences(myComponent);
    GuiUtils.removePotentiallyLeakingReferences(myProgress);
    myInfo = null;
  }

  private boolean isDisposed() {
    return myDisposed;
  }
}

class ProgressButton {
  @NotNull
  final InplaceButton button;
  @NotNull
  final Runnable updateAction;

  ProgressButton(@NotNull InplaceButton button, @NotNull Runnable updateAction) {
    this.button = button;
    this.updateAction = updateAction;
  }
}