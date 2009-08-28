package com.intellij.openapi.progress.util;

import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 19, 2004
 * Time: 5:10:44 PM
 * To change this template use File | Settings | File Templates.
 */
public class ProgressWindowWithNotification extends ProgressWindow {
  private final LinkedList<ProgressIndicatorListener> myListeners = new LinkedList<ProgressIndicatorListener>();

  public ProgressWindowWithNotification(boolean shouldShowCancel, Project project) {
    super(shouldShowCancel, project);
  }

  public ProgressWindowWithNotification(boolean shouldShowCancel, boolean shouldShowBackground, Project project) {
    super(shouldShowCancel, shouldShowBackground, project);
  }

  public ProgressWindowWithNotification(boolean shouldShowCancel, boolean shouldShowBackground, Project project, String cancelText) {
    super(shouldShowCancel, shouldShowBackground, project, cancelText);
  }

  public ProgressWindowWithNotification(boolean shouldShowCancel, boolean shouldShowBackground, Project project, JComponent parentComponent, String cancelText) {
    super(shouldShowCancel, shouldShowBackground, project, parentComponent, cancelText);
  }

  public void cancel() {
    super.cancel();
    for (Iterator<ProgressIndicatorListener> iterator = myListeners.iterator(); iterator.hasNext();) {
      ProgressIndicatorListener progressIndicatorListener = iterator.next();
      progressIndicatorListener.cancelled();
    }
  }

  public synchronized void stop() {
    for (Iterator<ProgressIndicatorListener> iterator = myListeners.iterator(); iterator.hasNext();) {
      ProgressIndicatorListener progressIndicatorListener = iterator.next();
      progressIndicatorListener.stopped();
    }
    super.stop();
  }

  public void addListener(ProgressIndicatorListener listener) {
    myListeners.addFirst(listener);
  }

  public void removeListener(ProgressIndicatorListener listener) {
    myListeners.remove(listener);
  }
}
