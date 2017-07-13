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
package com.intellij.openapi.wm.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.FocusWatcher;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.util.containers.WeakHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class WindowWatcher implements PropertyChangeListener{
  private static final Logger LOG=Logger.getInstance("#com.intellij.openapi.wm.impl.WindowWatcher");
  private final Object myLock = new Object();
  private final Map<Window, WindowInfo> myWindow2Info = new WeakHashMap<>();
  /**
   * Currenly focused window (window which has focused component). Can be {@code null} if there is no focused
   * window at all.
   */
  private Window myFocusedWindow;
  /**
   * Contains last focused window for each project.
   */
  private final HashSet myFocusedWindows = new HashSet();
  @NonNls protected static final String FOCUSED_WINDOW_PROPERTY = "focusedWindow";

  WindowWatcher() {}

  /**
   * This method should get notifications abount changes of focused window.
   * Only {@code focusedWindow} property is acceptable.
   * @throws IllegalArgumentException if property name isn't {@code focusedWindow}.
   */
  public final void propertyChange(final PropertyChangeEvent e){
    if(LOG.isDebugEnabled()){
      LOG.debug("enter: propertyChange("+e+")");
    }
    if(!FOCUSED_WINDOW_PROPERTY.equals(e.getPropertyName())){
      throw new IllegalArgumentException("unknown property name: "+e.getPropertyName());
    }
    synchronized(myLock){
      final Window window=(Window)e.getNewValue();
      if(window==null || ApplicationManager.getApplication().isDisposed()){
        return;
      }
      if(!myWindow2Info.containsKey(window)){
        myWindow2Info.put(window,new WindowInfo(window, true));
      }
      myFocusedWindow=window;
      final Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myFocusedWindow));
      for (Iterator i = myFocusedWindows.iterator(); i.hasNext();) {
        final Window w = (Window)i.next();
        final DataContext dataContext = DataManager.getInstance().getDataContext(w);
        if (project == CommonDataKeys.PROJECT.getData(dataContext)) {
          i.remove();
        }
      }
      myFocusedWindows.add(myFocusedWindow);
      // Set new root frame
      final IdeFrameImpl frame;
      if(window instanceof IdeFrameImpl){
        frame=(IdeFrameImpl)window;
      }else{
        frame=(IdeFrameImpl)SwingUtilities.getAncestorOfClass(IdeFrameImpl.class,window);
      }
      if(frame!=null){
        JOptionPane.setRootFrame(frame);
      }
    }
    if(LOG.isDebugEnabled()){
      LOG.debug("exit: propertyChange()");
    }
  }

  final void dispatchComponentEvent(final ComponentEvent e){
    final int id=e.getID();
    if(WindowEvent.WINDOW_CLOSED == id ||
       (ComponentEvent.COMPONENT_HIDDEN == id && e.getSource() instanceof Window)){
      dispatchHiddenOrClosed((Window)e.getSource());
    }
    // Clear obsolete reference on root frame
    if(WindowEvent.WINDOW_CLOSED==id){
      final Window window=(Window)e.getSource();
      if(JOptionPane.getRootFrame()==window){
        JOptionPane.setRootFrame(null);
      }
    }
  }

  private void dispatchHiddenOrClosed(final Window window){
    if(LOG.isDebugEnabled()){
      LOG.debug("enter: dispatchClosed("+window+")");
    }
    synchronized(myLock){
      final WindowInfo info=myWindow2Info.get(window);
      if(info!=null){
        final FocusWatcher focusWatcher=info.myFocusWatcherRef.get();
        if(focusWatcher!=null){
          focusWatcher.deinstall(window);
        }
        myWindow2Info.remove(window);
      }
    }
    // Now, we have to recalculate focused window if currently focused
    // window is being closed.
    if(myFocusedWindow==window){
      if(LOG.isDebugEnabled()){
        LOG.debug("currently active window should be closed");
      }
      myFocusedWindow=myFocusedWindow.getOwner();
      if (LOG.isDebugEnabled()) {
        LOG.debug("new active window is "+myFocusedWindow);
      }
    }
    for(Iterator i=myFocusedWindows.iterator();i.hasNext();){
      final Window activeWindow = (Window)i.next();
      if (activeWindow == window) {
        final Window newActiveWindow = activeWindow.getOwner();
        i.remove();
        if (newActiveWindow != null) {
          myFocusedWindows.add(newActiveWindow);
        }
        break;
      }
    }
    // Remove invalid infos for garbage collected windows
    for(Iterator i=myWindow2Info.values().iterator();i.hasNext();){
      final WindowInfo info=(WindowInfo)i.next();
      if(info.myFocusWatcherRef.get()==null){
        if (LOG.isDebugEnabled()) {
          LOG.debug("remove collected info");
        }
        i.remove();
      }
    }
  }

  public final Window getFocusedWindow(){
    synchronized(myLock){
      return myFocusedWindow;
    }
  }

  @Nullable
  public final Component getFocusedComponent(@Nullable final Project project) {
    synchronized(myLock){
      final Window window=getFocusedWindowForProject(project);
      if(window==null){
        return null;
      }
      return getFocusedComponent(window);
    }
  }


  public final Component getFocusedComponent(@NotNull final Window window){
    synchronized(myLock){
      final WindowInfo info=myWindow2Info.get(window);
      if(info==null){ // it means that we don't manage this window, so just return standard focus owner
        // return window.getFocusOwner();
        // TODO[vova,anton] usage of getMostRecentFocusOwner is experimental. But it seems suitable here.
        return window.getMostRecentFocusOwner();
      }
      final FocusWatcher focusWatcher=info.myFocusWatcherRef.get();
      if(focusWatcher!=null){
        final Component focusedComponent = focusWatcher.getFocusedComponent();
        if(focusedComponent != null && focusedComponent.isShowing()){
          return focusedComponent;
        }
        else{
          return null;
        }
      }else{
         // info isn't valid, i.e. window was garbage collected, so we need the remove invalid info
        // and return null
        myWindow2Info.remove(window);
        return null;
      }
    }
  }

  @Nullable
  public FocusWatcher getFocusWatcherFor(Component c) {
    final Window window = SwingUtilities.getWindowAncestor(c);
    final WindowInfo info = myWindow2Info.get(window);
    return info == null ? null : info.myFocusWatcherRef.get();
  }

  /**
   * @param project may be null (for example, if no projects are opened)
   */
  @Nullable
  public final Window suggestParentWindow(@Nullable final Project project){
    synchronized(myLock){
      Window window=getFocusedWindowForProject(project);
      if(window==null){
        if (project != null) {
          return (Window)WindowManagerEx.getInstanceEx().findFrameFor(project);
        }
        else{
          return null;
        }
      }

      LOG.assertTrue(window.isDisplayable());
      LOG.assertTrue(window.isShowing());

      while(window!=null){
        // skip all windows until found forst dialog or frame
        if(!(window instanceof Dialog)&&!(window instanceof Frame)){
          window=window.getOwner();
          continue;
        }
        // skip not visible and disposed/not shown windows
        if(!window.isDisplayable()||!window.isShowing()){
          window = window.getOwner();
          continue;
        }
        // skip windows that have not associated WindowInfo
        final WindowInfo info=myWindow2Info.get(window);
        if(info==null){
          window=window.getOwner();
          continue;
        }
        if(info.mySuggestAsParent){
          return window;
        }else{
          window=window.getOwner();
        }
      }
      return null;
    }
  }

  public final void doNotSuggestAsParent(final Window window) {
    if(LOG.isDebugEnabled()){
      LOG.debug("enter: doNotSuggestAsParent("+window+")");
    }
    synchronized(myLock){
      final WindowInfo info=myWindow2Info.get(window);
      if(info==null){
        myWindow2Info.put(window,new WindowInfo(window, false));
      }else{
        info.mySuggestAsParent=false;
      }
    }
  }

  /**
   * @return active window for specified {@code project}. There is only one window
   * for project can be at any point of time.
   */
  @Nullable
  private Window getFocusedWindowForProject(@Nullable final Project project) {
    //todo[anton,vova]: it is possible that returned wnd is not contained in myFocusedWindows; investigate
    outer: for(Iterator i=myFocusedWindows.iterator();i.hasNext();){
      Window window=(Window)i.next();
      while(!window.isDisplayable()||!window.isShowing()){ // if window isn't visible then gets its first visible ancestor
        window=window.getOwner();
        if(window==null){
          continue outer;
        }
      }
      final DataContext dataContext = DataManager.getInstance().getDataContext(window);
      if (project == CommonDataKeys.PROJECT.getData(dataContext)) {
        return window;
      }
    }
    return null;
  }

  private static final class WindowInfo {
    public final WeakReference<FocusWatcher> myFocusWatcherRef;
    public boolean mySuggestAsParent;

    public WindowInfo(final Window window,final boolean suggestAsParent){
      final FocusWatcher focusWatcher=new FocusWatcher();
      focusWatcher.install(window);
      myFocusWatcherRef= new WeakReference<>(focusWatcher);
      mySuggestAsParent=suggestAsParent;
    }

  }


}
