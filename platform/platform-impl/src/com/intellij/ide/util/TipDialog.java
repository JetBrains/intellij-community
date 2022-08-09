// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.TipsOfTheDayUsagesCollector;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;

public final class TipDialog extends DialogWrapper {
  private final TipPanel myTipPanel;
  private final boolean myShowingOnStartup;

  TipDialog(@NotNull final Project project) {
    super(project, true);
    setModal(false);
    setTitle(IdeBundle.message("title.tip.of.the.day"));
    setCancelButtonText(CommonBundle.getCloseButtonText());
    myTipPanel = new TipPanel(project, getDisposable());
    setDoNotAskOption(myTipPanel);
    myShowingOnStartup = myTipPanel.isToBeShown();
    setHorizontalStretch(1.33f);
    setVerticalStretch(1.25f);
    init();
  }

  @NotNull
  @Override
  protected DialogStyle getStyle() {
    return DialogStyle.COMPACT;
  }

  @Override
  protected JComponent createSouthPanel() {
    JComponent component = super.createSouthPanel();
    component.setBorder(JBUI.Borders.empty(8, 12));
    return component;
  }

  @Override
  public void doCancelAction() {
    super.doCancelAction();
    TipsOfTheDayUsagesCollector.triggerDialogClosed(myShowingOnStartup);
  }

  @Override
  protected Action @NotNull [] createActions() {
    if (Registry.is("ide.show.open.button.in.tip.dialog")) {
      return new Action[]{new OpenTipsAction(), myTipPanel.myPreviousTipAction, myTipPanel.myNextTipAction, getCancelAction()};
    }
    return new Action[]{myTipPanel.myPreviousTipAction, myTipPanel.myNextTipAction, getCancelAction()};
  }

  @Override
  protected JComponent createCenterPanel() {
    return myTipPanel;
  }

  private final class OpenTipsAction extends AbstractAction {
    private static final String LAST_OPENED_TIP_PATH = "last.opened.tip.path";

    OpenTipsAction() {
      super(IdeBundle.message("action.open.tip"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
      FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, true)
        .withFileFilter(file -> Comparing.equal(file.getExtension(), "html", file.isCaseSensitive()));
      String value = propertiesComponent.getValue(LAST_OPENED_TIP_PATH);
      VirtualFile lastOpenedTip = value != null ? LocalFileSystem.getInstance().findFileByPath(value) : null;
      VirtualFile[] pathToSelect = lastOpenedTip != null ? new VirtualFile[]{lastOpenedTip} : VirtualFile.EMPTY_ARRAY;
      VirtualFile[] choose = FileChooserFactory.getInstance().createFileChooser(descriptor, null, myTipPanel).choose(null, pathToSelect);
      if (choose.length > 0) {
        ArrayList<TipAndTrickBean> tips = new ArrayList<>();
        for (VirtualFile file : choose) {
          TipAndTrickBean tip = new TipAndTrickBean();
          tip.fileName = file.getPath();
          tip.featureId = null;
          tips.add(tip);
          propertiesComponent.setValue(LAST_OPENED_TIP_PATH, file.getPath());
        }
        myTipPanel.setTips(tips);
      }
    }
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPreferredFocusedComponent;
  }
}
