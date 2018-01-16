// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.panel;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.panel.GridBagPanelBuilder;
import com.intellij.openapi.ui.panel.ProgressPanel;
import com.intellij.openapi.ui.panel.ProgressPanelBuilder;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ProgressPanelBuilderImpl implements ProgressPanelBuilder, GridBagPanelBuilder {
  private static final Color SEPARATOR_COLOR = new JBColor(Gray.xC9, Gray.x55);

  private final JProgressBar myProgressBar;
  private String initialLabelText;
  private boolean labelAbove = true;

  private Runnable cancelAction;
  private Runnable resumeAction;
  private Runnable pauseAction;

  private boolean cancelAsButton;
  private boolean smallVariant;

  private boolean commentEnabled = true;
  private boolean topSeparatorEnabled;

  private boolean valid = true;

  ProgressPanelBuilderImpl(JProgressBar progressBar) {
    myProgressBar = progressBar;
  }

  @Override
  public ProgressPanelBuilder withLabel(@NotNull String text) {
    initialLabelText = text;
    return this;
  }

  @Override
  public ProgressPanelBuilder moveLabelLeft() {
    labelAbove = false;
    return this;
  }

  @Override
  public ProgressPanelBuilder withCancel(@NotNull Runnable cancelAction) {
    this.cancelAction = cancelAction;
    valid = resumeAction == null && pauseAction == null;
    return this;
  }

  @Override
  public ProgressPanelBuilder andCancelAsButton() {
    this.cancelAsButton = true;
    return this;
  }

  @Override
  public ProgressPanelBuilder withResume(@NotNull Runnable playAction) {
    this.resumeAction = playAction;
    valid = pauseAction != null && cancelAction == null;
    return this;
  }

  @Override
  public ProgressPanelBuilder withPause(@NotNull Runnable pauseAction) {
    this.pauseAction = pauseAction;
    valid = resumeAction != null && cancelAction == null;
    return this;
  }


  @Override
  public ProgressPanelBuilder andSmallIcons() {
    this.smallVariant = true;
    return this;
  }

  @Override
  public ProgressPanelBuilder withoutComment() {
    this.commentEnabled = false;
    return this;
  }

  @Override
  public ProgressPanelBuilder withTopSeparator() {
    this.topSeparatorEnabled = true;
    return this;
  }

  @Override
  @NotNull
  public JPanel createPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gc = new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
                                                   null, 0, 0);
    addToPanel(panel, gc);
    return panel;
  }

  @Override
  public boolean constrainsValid() {
    return valid;
  }

  @Override
  public void addToPanel(JPanel panel, GridBagConstraints gc) {
    if (constrainsValid()) {
      new LabeledPanelImpl().addToPanel(panel, gc);
    }
  }

  @Override
  public int gridWidth() {
    int width = labelAbove ? 1 : 2;
    width += (cancelAction != null || resumeAction != null && pauseAction != null) ? 1 : 0;
    return width;
  }

  private class LabeledPanelImpl extends ProgressPanel {
    private final JLabel label;
    private final JLabel comment;

    private String myCommentText = emptyComment();
    private boolean myServiceComment = false;

    private InplaceButton button;
    private IconButton cancelIcon;
    private IconButton resumeIcon;
    private IconButton pauseIcon;

    private SeparatorComponent mySeparatorComponent = new SeparatorComponent(SEPARATOR_COLOR, SeparatorOrientation.HORIZONTAL);

    private State state = State.PLAYING;

    private LabeledPanelImpl() {
      label = new JLabel(StringUtil.isNotEmpty(initialLabelText) ? initialLabelText : "");

      comment = new JLabel(myCommentText);
      comment.setForeground(Gray.x78);
      if (SystemInfo.isMac) {
        Font font = comment.getFont();
        float size = font.getSize2D();
        Font smallFont = font.deriveFont(size - 2.0f);
        comment.setFont(smallFont);
      }

      if (StringUtil.isNotEmpty(initialLabelText)) {
        Dimension size = comment.getPreferredSize();
        size.width = label.getMinimumSize().width;
        comment.setMinimumSize(size);
      }

      cancelIcon = new IconButton(null,
                                  smallVariant ? AllIcons.Process.StopSmall : AllIcons.Process.Stop,
                                  smallVariant ? AllIcons.Process.StopSmallHovered : AllIcons.Process.StopHovered);
      resumeIcon = new IconButton(null,
                                  smallVariant ? AllIcons.Process.ProgressResumeSmall : AllIcons.Process.ProgressResume,
                                  smallVariant ? AllIcons.Process.ProgressResumeSmallHover : AllIcons.Process.ProgressResumeHover);
      pauseIcon = new IconButton(null,
                                 smallVariant ? AllIcons.Process.ProgressPauseSmall : AllIcons.Process.ProgressPause,
                                 smallVariant ? AllIcons.Process.ProgressPauseSmallHover : AllIcons.Process.ProgressPauseHover);
    }

    private String emptyComment() {
      return commentEnabled ? " " : "";
    }

    @Override
    public String getLabelText() {
      return label.getText();
    }

    @Override
    public void setLabelText(String labelText) {
      label.setText(StringUtil.isNotEmpty(labelText) ? labelText : "");

      if (StringUtil.isNotEmpty(labelText)) {
        Dimension size = comment.getPreferredSize();
        size.width = label.getMinimumSize().width;
        comment.setMinimumSize(size);
      }
    }

    @Override
    public String getCommentText() {
      return myServiceComment ? myCommentText : comment.getText();
    }

    @Override
    public void setCommentText(String commentText) {
      if (commentEnabled) {
        setCommentText(commentText, false);
      }
    }

    @Override
    public void setSeparatorEnabled(boolean enabled) {
      mySeparatorComponent.setVisible(enabled);
    }

    private void setCommentText(String commentText, boolean serviceComment) {
      if (serviceComment) {
        myServiceComment = commentText != null;
        comment.setText(commentText == null ? myCommentText : commentText);
      }
      else if (!myServiceComment) {
        myCommentText = StringUtil.isNotEmpty(commentText) ? commentText : emptyComment();
        comment.setText(myCommentText);
      }
    }

    @Override
    public State getState() {
      return state;
    }

    void addToPanel(JPanel panel, GridBagConstraints gc) {
      gc.gridx = 0;
      gc.anchor = GridBagConstraints.LINE_START;
      gc.fill = GridBagConstraints.HORIZONTAL;

      if (topSeparatorEnabled) {
        gc.insets = JBUI.insets(8, 0);
        gc.gridwidth = gridWidth();
        gc.weightx = 1.0;
        panel.add(mySeparatorComponent, gc);
        gc.gridy++;
      }

      gc.weightx = 0.0;
      gc.gridwidth = 1;
      gc.insets = JBUI.insets(topSeparatorEnabled || smallVariant ? 0 : 12, 13, 0, labelAbove ? 13 : 0);
      panel.add(label, gc);

      if (labelAbove) {
        gc.insets = JBUI.insets(4, 13, 4, 0);
        gc.gridy++;
      }
      else {
        gc.insets = JBUI.insets(topSeparatorEnabled || smallVariant ? 2 : 14, 12, 0, 0);
        gc.gridx++;
      }

      gc.weightx = 1.0;
      panel.add(myProgressBar, gc);
      gc.gridx++;

      myProgressBar.putClientProperty(LABELED_PANEL_PROPERTY, this);

      gc.weightx = 0.0;
      gc.insets = JBUI.insets(labelAbove || topSeparatorEnabled || smallVariant ? 0 : 14, 10, 0, 13);

      if (cancelAction != null) {
        if (cancelAsButton) {
          JButton cancelButton = new JButton("Cancel");
          cancelButton.addActionListener((e) -> cancelAction.run());
          panel.add(cancelButton, gc);
        }
        else {
          button = new InplaceButton(cancelIcon, a -> {
            button.setPainting(false);
            state = State.CANCELLED;
            cancelAction.run();
          }).setFillBg(false);
        }
      }
      else if (resumeAction != null && pauseAction != null) {
        button = new InplaceButton(pauseIcon, a -> {
          if (state == State.PLAYING) {
            button.setIcons(resumeIcon);
            state = State.PAUSED;
            setCommentText("Paused", true);
            pauseAction.run();
          }
          else {
            button.setIcons(pauseIcon);
            state = State.PLAYING;
            setCommentText("Pause", true);
            resumeAction.run();
          }
        }).setFillBg(false);
      }

      if (button != null) {
        button.setMinimumSize(button.getPreferredSize());

        if (commentEnabled) {
          button.addMouseListener(new HoverListener());
        }

        gc.anchor = GridBagConstraints.EAST;
        gc.fill = GridBagConstraints.NONE;
        panel.add(button, gc);
      }

      if (commentEnabled) {
        gc.gridy++;
        gc.gridx = labelAbove ? 0 : 1;
        gc.insets = labelAbove ? JBUI.insets(-1, 13, 0, 13) : JBUI.insets(-1, 12, 0, 13);
        gc.weightx = 1.0;
        gc.anchor = GridBagConstraints.LINE_START;
        gc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(comment, gc);
      }

      gc.gridy++;
    }

    private class HoverListener extends MouseAdapter {
      @Override
      public void mouseEntered(MouseEvent e) {
        if (cancelAction != null) {
          setCommentText("Cancel", true);
        }
        else if (resumeAction != null && pauseAction != null) {
          setCommentText(state == State.PLAYING ? "Pause" : "Resume", true);
        }
        else {
          setCommentText(null, true);
        }
      }

      @Override
      public void mouseExited(MouseEvent e) {
        setCommentText(state != State.PAUSED ? null : "Paused", true);
      }
    }
  }
}
