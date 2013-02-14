
/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class TipDialog extends DialogWrapper{
  private final TipPanel myTipPanel;

  public TipDialog(){
    super(true);
    setModal(false);
    setTitle(IdeBundle.message("title.tip.of.the.day"));
    setCancelButtonText(CommonBundle.getCloseButtonText());
    myTipPanel = new TipPanel();
    myTipPanel.nextTip();
    setHorizontalStretch(1.33f);
    setVerticalStretch(1.25f);
    init();
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
    }

    public void actionPerformed(ActionEvent e){
      myTipPanel.nextTip();
    }
  }
}
