// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.Gray;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.HTMLEditorKitBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.util.Pair.pair;

public final class LegalNoticeDialog extends DialogWrapper {
  public static final class Builder {
    private final @NlsContexts.DialogTitle String title;
    private final @NlsContexts.DialogMessage String message;
    private Project project;
    private Component parent;
    private @NlsContexts.Button String cancelText;
    private Pair<@Nls String, Integer> customAction;

    private Builder(@NlsContexts.DialogTitle String title, @NlsContexts.DialogMessage String message) {
      this.title = title;
      this.message = message;
    }

    public Builder withParent(Project project) {
      this.project = project;
      return this;
    }

    public Builder withParent(Component parent) {
      this.parent = parent;
      return this;
    }

    public Builder withCancelText(@NotNull @Nls String text) {
      cancelText = text;
      return this;
    }

    public Builder withCustomAction(@NotNull @Nls String text, int exitCode) {
      assert exitCode >= DialogWrapper.NEXT_USER_EXIT_CODE;
      customAction = pair(text, exitCode);
      return this;
    }

    public int show() {
      LegalNoticeDialog dialog = new LegalNoticeDialog(this);
      dialog.show();
      return dialog.getExitCode();
    }
  }

  public static Builder build(@NotNull @NlsContexts.DialogTitle String title, @NotNull @NlsContexts.DialogMessage String message) {
    return new Builder(title, message);
  }

  private final Builder myBuilder;

  private LegalNoticeDialog(Builder builder) {
    super(builder.project, builder.parent, true, IdeModalityType.PROJECT);
    myBuilder = builder;
    setTitle(builder.title);
    setOKButtonText(CommonBundle.message("button.accept"));
    setCancelButtonText(builder.cancelText != null ? builder.cancelText : CommonBundle.message("button.decline"));
    init();
    pack();
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel iconPanel = new JPanel(new BorderLayout());
    iconPanel.add(new JBLabel(AllIcons.General.WarningDialog), BorderLayout.NORTH);

    JEditorPane messageArea = new JEditorPane();
    messageArea.setEditorKit(HTMLEditorKitBuilder.simple());
    messageArea.setEditable(false);
    messageArea.setPreferredSize(JBUI.size(500, 100));
    messageArea.setBorder(new CompoundBorder(BorderFactory.createLineBorder(Gray._200), JBUI.Borders.empty(3)));
    messageArea.setText(UIUtil.toHtml(myBuilder.message));

    JPanel panel = new JPanel(new BorderLayout(JBUIScale.scale(3), 0));
    panel.add(iconPanel, BorderLayout.WEST);
    panel.add(messageArea, BorderLayout.CENTER);
    return panel;
  }

  @Override
  protected Action @NotNull [] createActions() {
    List<Action> actions = new ArrayList<>();
    actions.add(getOKAction());
    if (myBuilder.customAction != null) {
      actions.add(new DialogWrapperExitAction(myBuilder.customAction.first, myBuilder.customAction.second));
    }
    actions.add(getCancelAction());
    return actions.toArray(new Action[0]);
  }
}
