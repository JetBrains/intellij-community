// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.TipsOfTheDayUsagesCollector;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;

public class TipDialog extends DialogWrapper {
  private static TipDialog ourInstance;


  private TipPanel myTipPanel;

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  public TipDialog() {
    super(WindowManagerEx.getInstanceEx().findVisibleFrame(), true);
    initialize();
  }

  public TipDialog(@NotNull final Window parent) {
    super(parent, true);
    initialize();
  }

  private void initialize() {
    setModal(false);
    setTitle(IdeBundle.message("title.tip.of.the.day"));
    setCancelButtonText(CommonBundle.getCloseButtonText());
    myTipPanel = new TipPanel();
    myTipPanel.setTips(ContainerUtil.newArrayList(TipAndTrickBean.EP_NAME.getExtensionList()));
    myTipPanel.nextTip();
    setDoNotAskOption(myTipPanel);
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
  @NotNull
  protected Action[] createActions() {
    if (ApplicationManager.getApplication().isInternal()) {
      return new Action[]{new OpenTipsAction(), new PreviousTipAction(), new NextTipAction(), getCancelAction()};
    }
    return new Action[]{new PreviousTipAction(), new NextTipAction(), getCancelAction()};
  }

  @Override
  protected JComponent createCenterPanel() {
    return myTipPanel;
  }

  @Override
  public void dispose() {
    super.dispose();
  }

  public static void showForProject(@Nullable Project project) {
    createForProject(project);
    ourInstance.show();
  }

  /**
   * @deprecated Use {@link #showForProject(Project)} instead
   */
  @Deprecated
  public static TipDialog createForProject(@Nullable Project project) {
    Window w = WindowManagerEx.getInstanceEx().suggestParentWindow(project);
    if (ourInstance != null && ourInstance.isVisible()) {
      ourInstance.dispose();
    }
    return ourInstance = (w == null) ? new TipDialog() : new TipDialog(w);
  }

  public static void hideForProject(@Nullable Project project) {
    if (ourInstance != null) {
      ourInstance.dispose();
      ourInstance = null;
    }
  }

  private class OpenTipsAction extends AbstractAction {
    private static final String LAST_OPENED_TIP_PATH = "last.opened.tip.path";

    OpenTipsAction() {
      super(IdeBundle.message("action.open.tip"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
      FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, true)
        .withFileFilter(file -> Comparing.equal(file.getExtension(), "html", SystemInfo.isFileSystemCaseSensitive));
      String value = propertiesComponent.getValue(LAST_OPENED_TIP_PATH);
      VirtualFile lastOpenedTip = value != null ? LocalFileSystem.getInstance().findFileByPath(value) : null;
      VirtualFile[] pathToSelect = lastOpenedTip != null ? new VirtualFile[]{lastOpenedTip} : VirtualFile.EMPTY_ARRAY;
      VirtualFile[] choose = FileChooserFactory.getInstance().createFileChooser(descriptor, null, myTipPanel).choose(null, pathToSelect);
      if (choose.length > 0) {
        ArrayList<TipAndTrickBean> tips = ContainerUtil.newArrayList();
        for (VirtualFile file : choose) {
          TipAndTrickBean tip = new TipAndTrickBean();
          tip.fileName = file.getPath();
          tip.featureId = null;
          tips.add(tip);
          propertiesComponent.setValue(LAST_OPENED_TIP_PATH, file.getPath());
        }
        myTipPanel.setTips(tips);
        myTipPanel.nextTip();
      }
    }
  }

  private class PreviousTipAction extends AbstractAction {
    PreviousTipAction() {
      super(IdeBundle.message("action.previous.tip"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      TipsOfTheDayUsagesCollector.trigger("previous.tip");
      myTipPanel.prevTip();
    }
  }

  private class NextTipAction extends AbstractAction {
    NextTipAction() {
      super(IdeBundle.message("action.next.tip"));
      putValue(DialogWrapper.DEFAULT_ACTION, Boolean.TRUE);
      putValue(DialogWrapper.FOCUSED_ACTION, Boolean.TRUE); // myPreferredFocusedComponent
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      TipsOfTheDayUsagesCollector.trigger("next.tip");
      myTipPanel.nextTip();
    }
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPreferredFocusedComponent;
  }
}
