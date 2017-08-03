/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.openapi.wm.impl.commands;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Expirable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.impl.FloatingDecorator;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Requests focus for the editor component.
 */
public final class RequestFocusInEditorComponentCmd extends FinalizableCommand{
  private JComponent myComponent;
  private final boolean myForced;
  private final ActionCallback myDoneCallback;

  private final IdeFocusManager myFocusManager;
  private final Expirable myTimestamp;

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.impl.commands.RequestFocusInEditorComponentCmd");

  public RequestFocusInEditorComponentCmd(@NotNull final EditorsSplitters splitters, IdeFocusManager
                                          focusManager, final Runnable finishCallBack, boolean forced){
    super(finishCallBack);

    boolean shouldLogFocuses = Registry.is("ide.log.focuses");
    if (shouldLogFocuses) {
      LOG.info(new Exception());
    }
    myComponent = null;
    final EditorWindow window = splitters.getCurrentWindow();
    if (window != null) {
      final EditorWithProviderComposite editor = window.getSelectedEditor();
      if (editor != null) {
        myComponent = editor.getPreferredFocusedComponent();
      }
    }

    myForced = forced;
    myFocusManager = focusManager;

    myDoneCallback = new ActionCallback();

    myTimestamp = myFocusManager.getTimestamp(true);
  }

  public ActionCallback getDoneCallback() {
    return myDoneCallback;
  }

  public final void run(){
    try{
      if (myTimestamp.isExpired()) {
        final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (owner != null && owner == myComponent) {
          myDoneCallback.setDone();
        } else {
          myDoneCallback.setRejected();
        }
      }


      final Window owner = myComponent != null ? SwingUtilities.getWindowAncestor(myComponent) : null;
      if(owner==null){
        myDoneCallback.setRejected();
        return;
      }

      final Window activeFrame = IdeFrameImpl.getActiveFrame();
      if (activeFrame != null && owner instanceof IdeFrameImpl && activeFrame != owner) {
        myDoneCallback.setRejected();
        return;
      }

      if(myComponent != null){
        final boolean forced = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() == null;
        myFocusManager.requestFocus(myComponent, myForced || forced).notifyWhenDone(myDoneCallback).doWhenDone(() -> {
          if (SystemInfo.isLinux && Registry.is("suppress.focus.stealing")) return;
          // if owner is active window or it has active child window which isn't floating decorator then
          // don't bring owner window to font. If we will make toFront every time then it's possible
          // the following situation:
          // 1. user perform refactoring
          // 2. "Do not show preview" dialog is popping up.
          // 3. At that time "preview" tool window is being activated and modal "don't show..." dialog
          // isn't active.
          if(!owner.isActive()){
            final Window activeWindow=getActiveWindow(owner.getOwnedWindows());
            if(activeWindow == null || (activeWindow instanceof FloatingDecorator)){
              //Thread.dumpStack();
              //System.out.println("------------------------------------------------------");
              owner.toFront();
            }
          }
        });
      } else {
        myDoneCallback.setRejected();
      }

    }finally{
      finish();
    }
  }

  /**
   * @return first active window from hierarchy with specified roots. Returns {@code null}
   * if there is no active window in the hierarchy.
   */
  @Nullable
  private static Window getActiveWindow(final Window[] windows) {
    for (Window window1 : windows) {
      Window window = window1;
      if (window.isShowing() && window.isActive()) {
        return window;
      }
      window = getActiveWindow(window.getOwnedWindows());
      if (window != null) {
        return window;
      }
    }
    return null;
  }
}
