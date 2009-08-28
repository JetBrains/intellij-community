package com.intellij.openapi.wm.impl;

import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowType;

import java.util.EventListener;

/**
 * @author Vladimir Kondratyev
 */
interface InternalDecoratorListener extends EventListener{

  public void anchorChanged(InternalDecorator source,ToolWindowAnchor anchor);

  public void autoHideChanged(InternalDecorator source,boolean autoHide);

  public void hidden(InternalDecorator source);

  public void hiddenSide(InternalDecorator source);

  public void resized(InternalDecorator source);

  public void activated(InternalDecorator source);

  public void typeChanged(InternalDecorator source,ToolWindowType type);

  public void sideStatusChanged(InternalDecorator source,boolean isSideTool);

}
