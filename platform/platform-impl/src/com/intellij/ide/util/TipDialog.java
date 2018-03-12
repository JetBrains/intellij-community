/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.util;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class TipDialog extends DialogWrapper{
  private TipPanel myTipPanel;

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  public TipDialog(){
    super(WindowManagerEx.getInstanceEx().findVisibleFrame(), true);
    initialize();
  }

  public TipDialog(@NotNull final Window parent) {
    super(parent, true);
    initialize();
  }

  private void initialize () {
      setModal(false);
      setTitle(IdeBundle.message("title.tip.of.the.day"));
      setCancelButtonText(CommonBundle.getCloseButtonText());
      myTipPanel = new TipPanel();
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

  @NotNull
  protected Action[] createActions(){
    return new Action[]{new PreviousTipAction(),new NextTipAction(),getCancelAction()};
  }

  protected JComponent createCenterPanel(){
    return myTipPanel;
  }

  public void dispose(){
    super.dispose();
  }

  public static TipDialog createForProject(final Project project) {
    final Window w = WindowManagerEx.getInstanceEx().suggestParentWindow(project);
    return (w == null) ? new TipDialog() : new TipDialog(w);
  }

  private class PreviousTipAction extends AbstractAction{
    public PreviousTipAction(){
      super(IdeBundle.message("action.previous.tip"));
    }

    public void actionPerformed(ActionEvent e){
      myTipPanel.prevTip();
    }
  }

  private class NextTipAction extends AbstractAction{
    public NextTipAction(){
      super(IdeBundle.message("action.next.tip"));
      putValue(DialogWrapper.DEFAULT_ACTION,Boolean.TRUE);
      putValue(DialogWrapper.FOCUSED_ACTION,Boolean.TRUE); // myPreferredFocusedComponent
    }

    public void actionPerformed(ActionEvent e){
      myTipPanel.nextTip();
    }
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPreferredFocusedComponent;
  }
}
