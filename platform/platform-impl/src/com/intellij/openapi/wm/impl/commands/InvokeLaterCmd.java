package com.intellij.openapi.wm.impl.commands;



/**
 * @author Vladimir Kondratyev
 */
public final class InvokeLaterCmd extends FinalizableCommand{
  private final Runnable myRunnable;

  public InvokeLaterCmd(final Runnable runnable,final Runnable finishCallBack){
    super(finishCallBack);
    myRunnable=runnable;
  }

  public void run(){
    try{
      myRunnable.run();
    }finally{
      finish();
    }
  }
}
