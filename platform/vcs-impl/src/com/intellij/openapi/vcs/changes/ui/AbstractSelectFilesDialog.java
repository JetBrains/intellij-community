// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;


@ApiStatus.Internal
public abstract class AbstractSelectFilesDialog extends DialogWrapper {
  private final @NlsContexts.Label String myPrompt;

  public AbstractSelectFilesDialog(Project project,
                                   boolean canBeParent,
                                   @Nullable VcsShowConfirmationOption confirmationOption,
                                   @Nullable @NlsContexts.Label String prompt) {
    super(project, canBeParent);
    myPrompt = prompt;

    if (confirmationOption != null && confirmationOption.isPersistent()) {
      setDoNotAskOption(new MyDoNotAskOption(confirmationOption));
    }
  }

  @Override
  protected void init() {
    super.init();

    ChangesTree changesTree = getFileList();
    if (changesTree instanceof AsyncChangesTree asyncChangesTree) {
      setOKActionEnabled(false);
      asyncChangesTree.invokeAfterRefresh(() -> {
        setOKActionEnabled(true);
      });
    }
  }

  @NotNull
  protected abstract ChangesTree getFileList();

  @Override
  protected JComponent createNorthPanel() {
    if (myPrompt != null) {
      final JLabel label = new JLabel(myPrompt);
      label.setUI(new MultiLineLabelUI());
      label.setBorder(JBUI.Borders.empty(5, 1));
      return label;
    }
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return getFileList();
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    DefaultActionGroup group = createToolbarActions();
    group.add(Separator.getInstance());
    group.add(ActionManager.getInstance().getAction(ChangesTree.GROUP_BY_ACTION_GROUP));
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("VcsSelectFilesDialog", group, true);

    TreeActionsToolbarPanel toolbarPanel = new TreeActionsToolbarPanel(toolbar, getFileList());

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(toolbarPanel, BorderLayout.NORTH);
    panel.add(ScrollPaneFactory.createScrollPane(getFileList()), BorderLayout.CENTER);

    return panel;
  }

  @NotNull
  protected DefaultActionGroup createToolbarActions() {
    return new DefaultActionGroup();
  }

  private static final class MyDoNotAskOption extends DoNotAskOption.Adapter {
    private final VcsShowConfirmationOption myConfirmationOption;

    private MyDoNotAskOption(@NotNull VcsShowConfirmationOption confirmationOption) {
      myConfirmationOption = confirmationOption;
    }

    @Override
    public boolean shouldSaveOptionsOnCancel() {
      return true;
    }

    @Override
    public void rememberChoice(boolean isSelected, int exitCode) {
      if (isSelected) {
        if (exitCode == DialogWrapper.OK_EXIT_CODE) {
          myConfirmationOption.setValue(VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY);
        }
        if (exitCode == DialogWrapper.CANCEL_EXIT_CODE) {
          myConfirmationOption.setValue(VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY);
        }
      }
    }
  }
}
