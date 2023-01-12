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
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Map;

import static javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT;

public final class TipDialog extends DialogWrapper {
  private final TipPanel myTipPanel;
  private final boolean myShowingOnStartup;
  private final boolean myShowActions;

  TipDialog(@NotNull final Project project, @NotNull final TipsSortingResult sortingResult) {
    super(project, true);
    setModal(false);
    setTitle(IdeBundle.message("title.tip.of.the.day"));
    setCancelButtonText(CommonBundle.getCloseButtonText());
    myTipPanel = new TipPanel(project, sortingResult, getDisposable());
    myTipPanel.addPropertyChangeListener(TipPanel.CURRENT_TIP_KEY.toString(), event -> {
      SwingUtilities.invokeLater(() -> adjustSizeToContent());
    });
    myShowActions = sortingResult.getTips().size() > 1;
    if (myShowActions) {
      setDoNotAskOption(myTipPanel);
    }
    myShowingOnStartup = myTipPanel.isToBeShown();
    init();
  }

  @Override
  public void show() {
    super.show();
    // For some reason OS reduces the height of the dialog after showing (XDecoratedPeer#handleCorrectInsets)
    // So we need to return preferred height back
    SwingUtilities.invokeLater(() -> adjustSizeToContent());
  }

  private void adjustSizeToContent() {
    if (isDisposed()) return;
    Dimension prefSize = getPreferredSize();
    Dimension minSize = getRootPane().getMinimumSize();
    int height = Math.max(prefSize.height, minSize.height);
    setSize(prefSize.width, height);
    // Hack to free space occupied by JBScrollBar
    // For some reason insets are recalculated inside JBViewport.ViewBorder#getBorderInsets()
    // but do not update during validation after dialog size change
    SwingUtilities.invokeLater(() -> myTipPanel.getContentPanel().getInsets());
  }

  @NotNull
  @Override
  protected DialogStyle getStyle() {
    return DialogStyle.COMPACT;
  }

  @Override
  protected JComponent createSouthPanel() {
    JComponent component = super.createSouthPanel();
    component.setBorder(JBUI.Borders.empty(13, 24, 15, 24));
    UIUtil.setBackgroundRecursively(component, TipUiSettings.getPanelBackground());

    String previousTipKey = "PreviousTip";
    component.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
      .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), previousTipKey);
    component.getActionMap().put(previousTipKey, myTipPanel.myPreviousTipAction);

    return component;
  }

  @Override
  public void doCancelAction() {
    super.doCancelAction();
    TipsOfTheDayUsagesCollector.triggerDialogClosed(myShowingOnStartup);

    Map<String, Boolean> tipIdToLikenessState = myTipPanel.getTipIdToLikenessStateMap();
    for (Map.Entry<String, Boolean> pair : tipIdToLikenessState.entrySet()) {
      String tipId = pair.getKey();
      Boolean likenessState = pair.getValue();
      TipsFeedback feedback = TipsFeedback.getInstance();
      feedback.setLikenessState(tipId, likenessState);
      if (likenessState != null) {
        feedback.scheduleFeedbackSending(tipId, likenessState);
      }
    }
  }

  @Override
  protected Action @NotNull [] createActions() {
    if (myShowActions) {
      if (Registry.is("ide.show.open.button.in.tip.dialog")) {
        return new Action[]{new OpenTipsAction(), myTipPanel.myPreviousTipAction, myTipPanel.myNextTipAction, getCancelAction()};
      }
      return new Action[]{myTipPanel.myPreviousTipAction, myTipPanel.myNextTipAction, getCancelAction()};
    }
    else {
      return new Action[]{getCancelAction()};
    }
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
        myTipPanel.setTips(new TipsSortingResult(tips));
      }
    }
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPreferredFocusedComponent;
  }
}
