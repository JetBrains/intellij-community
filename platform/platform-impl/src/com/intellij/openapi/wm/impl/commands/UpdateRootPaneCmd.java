package com.intellij.openapi.wm.impl.commands;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Vladimir Kondratyev
 */
public final class UpdateRootPaneCmd extends FinalizableCommand{
  private final JRootPane myRootPane;

  public UpdateRootPaneCmd(@NotNull final JRootPane rootPane, final Runnable finishCallBack){
    super(finishCallBack);
    myRootPane=rootPane;
  }

  public void run(){
    try{
      myRootPane.revalidate();
      myRootPane.repaint();
    }finally{
      finish();
    }
  }
}

