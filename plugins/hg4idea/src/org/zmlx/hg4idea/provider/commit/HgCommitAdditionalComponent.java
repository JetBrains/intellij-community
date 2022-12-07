// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.provider.commit;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import com.intellij.vcs.commit.AmendCommitHandler;
import com.intellij.vcs.commit.AmendCommitModeListener;
import com.intellij.vcs.commit.ToggleAmendCommitOption;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgBundle;

import javax.swing.*;
import java.awt.*;

import static org.zmlx.hg4idea.provider.commit.HgCommitOptionsKt.setCommitSubrepositories;

/**
 * Commit options for hg
 */
public class HgCommitAdditionalComponent implements RefreshableOnComponent, AmendCommitModeListener, Disposable {
  @NotNull private final JPanel myPanel;
  @NotNull private final JCheckBox myCommitSubrepos;
  @NotNull private final CheckinProjectPanel myCommitPanel;
  @NotNull private final CommitContext myCommitContext;
  @Nullable private final ToggleAmendCommitOption myAmendOption;

  HgCommitAdditionalComponent(@NotNull CheckinProjectPanel panel,
                              @NotNull CommitContext commitContext,
                              boolean hasSubrepos,
                              boolean showAmendOption) {
    myCommitPanel = panel;
    myCommitContext = commitContext;
    myAmendOption = showAmendOption ? new ToggleAmendCommitOption(myCommitPanel, this) : null;

    myCommitSubrepos = new JCheckBox(HgBundle.message("repositories.commit.subs"), false);
    myCommitSubrepos.setVisible(hasSubrepos);
    myCommitSubrepos.setToolTipText(XmlStringUtil.wrapInHtml(HgBundle.message("repositories.commit.subs.tooltip")));
    myCommitSubrepos.addActionListener(e -> updateAmendState(!myCommitSubrepos.isSelected()));

    GridBag gb = new GridBag().
      setDefaultInsets(JBUI.insets(2)).
      setDefaultAnchor(GridBagConstraints.WEST).
      setDefaultWeightX(1).
      setDefaultFill(GridBagConstraints.HORIZONTAL);
    myPanel = new JPanel(new GridBagLayout());
    if (myAmendOption != null) myPanel.add(myAmendOption, gb.nextLine().next());
    myPanel.add(myCommitSubrepos, gb.nextLine().next());

    getAmendHandler().addAmendCommitModeListener(this, this);
  }

  @NotNull
  private AmendCommitHandler getAmendHandler() {
    return myCommitPanel.getCommitWorkflowHandler().getAmendCommitHandler();
  }

  @Override
  public void dispose() {
  }

  @Override
  public void amendCommitModeToggled() {
    updateCommitSubreposState();
  }

  @Override
  public void saveState() {
    setCommitSubrepositories(myCommitContext, myCommitSubrepos.isSelected());
  }

  @Override
  public void restoreState() {
    updateCommitSubreposState();
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  public boolean isAmend() {
    return getAmendHandler().isAmendCommitMode();
  }

  private void updateCommitSubreposState() {
    boolean isAmendMode = isAmend();

    myCommitSubrepos.setEnabled(!isAmendMode);
    if (isAmendMode) myCommitSubrepos.setSelected(false);
  }

  private void updateAmendState(boolean enable) {
    getAmendHandler().setAmendCommitModeTogglingEnabled(enable);
    if (myAmendOption != null) myAmendOption.setEnabled(enable);
    if (!enable) getAmendHandler().setAmendCommitMode(false);
  }
}
