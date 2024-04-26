// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.ui.InplaceButton;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class InlineProgressIndicator extends ProgressIndicatorBase implements Disposable {
  protected final TextPanel text;
  protected final TextPanel text2;
  private final @NotNull List<ProgressButton> eastButtons;

  protected final @NotNull JProgressBar progress;

  protected final JPanel component;

  private final boolean isCompact;
  private final TaskInfo info;

  private final TextPanel processName;
  private boolean isDisposed;

  public InlineProgressIndicator(boolean compact, @NotNull TaskInfo processInfo) {
    isCompact = compact;
    info = processInfo;

    progress = new JProgressBar(SwingConstants.HORIZONTAL);
    progress.setOpaque(false);
    UIUtil.applyStyle(UIUtil.ComponentStyle.MINI, progress);

    text = new TextPanel();
    text2 = new TextPanel();
    processName = new TextPanel();
    eastButtons = createEastButtons();
    component = createComponent();
  }

  protected @NotNull JPanel createComponent() {
    MyComponent component = new MyComponent(isCompact, processName);
    if (isCompact) {
      component.setLayout(new BorderLayout(2, 0));
      createCompactTextAndProgress(component);
      component.add(createButtonPanel(ContainerUtil.map(eastButtons, b -> b.button)), BorderLayout.EAST);
      component.setToolTipText(info.getTitle() + ". " + IdeBundle.message("progress.text.clickToViewProgressWindow"));
    }
    else {
      component.setLayout(new BorderLayout());
      processName.setText(info.getTitle());
      component.add(processName, BorderLayout.NORTH);
      processName.setForeground(UIUtil.getPanelBackground().brighter().brighter());
      processName.setBorder(JBUI.Borders.empty(2));

      final NonOpaquePanel content = new NonOpaquePanel(new BorderLayout());
      content.setBorder(JBUI.Borders.empty(2, 2, 2, info.isCancellable() ? 2 : 4));
      component.add(content, BorderLayout.CENTER);

      content.add(createButtonPanel(ContainerUtil.map(eastButtons, b -> withBorder(b.button))), BorderLayout.EAST);
      content.add(text, BorderLayout.NORTH);
      content.add(progress, BorderLayout.CENTER);
      content.add(text2, BorderLayout.SOUTH);

      component.setBorder(JBUI.Borders.empty(2));
    }
    UIUtil.uiTraverser(component).forEach(o -> ((JComponent)o).setOpaque(false));

    if (!isCompact) {
      processName.recomputeSize();
      text.recomputeSize();
      text2.recomputeSize();
    }
    return component;
  }

  protected void createCompactTextAndProgress(@NotNull JPanel component) {
    JPanel textAndProgress = new NonOpaquePanel(new BorderLayout());
    textAndProgress.add(text, BorderLayout.CENTER);

    final NonOpaquePanel progressWrapper = new NonOpaquePanel(new BorderLayout());
    progressWrapper.setBorder(JBUI.Borders.empty(0, 4));
    progressWrapper.add(progress, BorderLayout.CENTER);

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

  private static @NotNull Wrapper withBorder(@NotNull InplaceButton button) {
    Wrapper wrapper = new Wrapper(button);
    wrapper.setBorder(JBUI.Borders.empty(0, 3, 0, 2));
    return wrapper;
  }

  protected @NotNull List<ProgressButton> createEastButtons() {
    return List.of(createCancelButton());
  }

  protected final @NotNull ProgressButton createCancelButton() {
    InplaceButton cancelButton = new InplaceButton(
      new IconButton(info.getCancelTooltipText(),
                     isCompact ? AllIcons.Process.StopSmall : AllIcons.Process.Stop,
                     isCompact ? AllIcons.Process.StopSmallHovered : AllIcons.Process.StopHovered),
      __ -> cancelRequest()).setFillBg(false);

    cancelButton.setVisible(info.isCancellable());

    return new ProgressButton(cancelButton, () -> cancelButton.setPainting(!isStopping()));
  }

  protected void cancelRequest() {
    cancel();
  }

  protected final void updateAndRepaint() {
    if (isDisposed()) {
      return;
    }

    updateProgressNow();

    component.repaint();
  }

  public void updateProgressNow() {
    if (isPaintingIndeterminate()) {
      progress.setIndeterminate(true);
    }
    else {
      progress.setIndeterminate(false);
      progress.setMinimum(0);
      progress.setMaximum(100);
    }
    if (getFraction() > 0) {
      progress.setValue((int)(getFraction() * 99 + 1));
    }

    String text = getText();
    String text2 = getText2();
    setTextValue(text != null ? text : "");
    setText2Value(text2 != null ? text2 : "");

    if (isCompact && StringUtil.isEmpty(getTextValue())) {
      setTextValue(info.getTitle());
    }

    if (isStopping()) {
      if (isCompact) {
        setTextValue(IdeBundle.message("progress.text.stopping", getTextValue()));
      }
      else {
        setProcessNameValue(IdeBundle.message("progress.text.stopping", info.getTitle()));
        setTextEnabled(false);
        setText2Enabled(false);
      }
      progress.setEnabled(false);
    }
    else {
      setTextEnabled(true);
      setText2Enabled(true);
      progress.setEnabled(true);
    }

    for (ProgressButton button : eastButtons) {
      button.updateAction.run();
    }
  }

  protected @Nullable String getTextValue() {
    return text.getText();
  }

  protected void setTextValue(@NlsContexts.ProgressText @NotNull String text) {
    this.text.setText(text);
  }

  protected void setTextEnabled(boolean value) {
    text.setEnabled(value);
  }

  protected @Nullable String getText2Value() {
    return text2.getText();
  }

  protected void setText2Value(@NlsContexts.ProgressDetails @NotNull String text) {
    text2.setText(text);
  }

  protected void setText2Enabled(boolean value) {
    text2.setEnabled(value);
  }

  protected void setProcessNameValue(@NlsContexts.ProgressTitle @NotNull String text) {
    processName.setText(text);
  }

  protected @NlsContexts.ProgressTitle String getProcessNameValue() {
    return processName.getText();
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

  @Override
  protected void onProgressChange() {
    queueProgressUpdate();
  }

  public @NotNull JComponent getComponent() {
    return component;
  }

  boolean isCompact() {
    return isCompact;
  }

  TaskInfo getInfo() {
    return info;
  }

  private final class MyComponent extends JPanel {
    private final boolean isCompact;
    private final JComponent myProcessName;

    private MyComponent(final boolean compact, @NotNull JComponent processName) {
      isCompact = compact;
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
      if (isCompact) {
        super.paintComponent(g);
        return;
      }

      GraphicsConfig c = GraphicsUtil.setupAAPainting(g);
      UISettings.setupAntialiasing(g);

      int arc = 8;
      Color bg = getBackground();
      Rectangle bounds = myProcessName.getBounds();
      Rectangle label = SwingUtilities.convertRectangle(myProcessName.getParent(), bounds, this);

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
    isDisposed = true;
  }

  private boolean isDisposed() {
    return isDisposed;
  }

  static final class ProgressButton {
    final @NotNull InplaceButton button;
    final @NotNull Runnable updateAction;

    ProgressButton(@NotNull InplaceButton button, @NotNull Runnable updateAction) {
      this.button = button;
      this.updateAction = updateAction;
    }
  }
}