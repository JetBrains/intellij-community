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
package com.intellij.testFramework;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.idea.Bombed;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Alarm;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.UIUtil;
import junit.framework.Assert;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.Calendar;
import java.util.Date;

/**
 * @author yole
 */
public class PlatformTestUtil {
  public static <T> void registerExtension(final ExtensionPointName<T> name, final T t, final Disposable parentDisposable) {
    registerExtension(Extensions.getRootArea(), name, t, parentDisposable);
  }

  public static <T> void registerExtension(final ExtensionsArea area, final ExtensionPointName<T> name, final T t, final Disposable parentDisposable) {
    final ExtensionPoint<T> extensionPoint = area.getExtensionPoint(name.getName());
    extensionPoint.registerExtension(t);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        extensionPoint.unregisterExtension(t);
      }
    });
  }

  protected static String toString(Object node) {
    if (node instanceof AbstractTreeNode) {
      return ((AbstractTreeNode)node).getTestPresentation();
    }
    else if (node == null) {
      return "NULL";
    }
    else {
      return node.toString();
    }
  }

  public static String print(JTree tree, boolean withSelection) {
    StringBuffer buffer = new StringBuffer();
    Object root = tree.getModel().getRoot();
    printImpl(tree, root, buffer, 0, withSelection);
    return buffer.toString();
  }

  private static void printImpl(JTree tree, Object root, StringBuffer buffer, int level, boolean withSelection) {
    DefaultMutableTreeNode defaultMutableTreeNode = (DefaultMutableTreeNode)root;
    boolean expanded = tree.isExpanded(new TreePath(defaultMutableTreeNode.getPath()));
    StringUtil.repeatSymbol(buffer, ' ', level);
    if (expanded && !defaultMutableTreeNode.isLeaf()) {
      buffer.append("-");
    }

    if (!expanded && !defaultMutableTreeNode.isLeaf()) {
      buffer.append("+");
    }

    final boolean selected = tree.getSelectionModel().isPathSelected(new TreePath(defaultMutableTreeNode.getPath()));

    if (withSelection && selected) {
      buffer.append("[");
    }

    final Object userObject = defaultMutableTreeNode.getUserObject();
    if (userObject != null) {
      buffer.append(toString(userObject));
    }
    else {
      buffer.append(defaultMutableTreeNode);
    }

    if (withSelection && selected) {
      buffer.append("]");
    }

    buffer.append("\n");
    int childCount = tree.getModel().getChildCount(root);
    if (expanded) {
      for (int i = 0; i < childCount; i++) {
        printImpl(tree, tree.getModel().getChild(root, i), buffer, level + 1, withSelection);
      }
    }
  }

  public static void assertTreeEqual(JTree tree, @NonNls String expected) {
    assertTreeEqual(tree, expected, false);
  }

  public static void assertTreeEqual(JTree tree, String expected, boolean checkSelected) {
    String treeStringPresentation = print(tree, checkSelected);
    Assert.assertEquals(expected, treeStringPresentation);
  }

  public static void waitForAlarm(final int delay) throws InterruptedException {
    final boolean[] invoked = new boolean[]{false};
    final Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
    alarm.addRequest(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            alarm.addRequest(new Runnable() {
              public void run() {
                invoked[0] = true;
              }
            }, delay);
          }
        });
      }
    }, delay);

    UIUtil.dispatchAllInvocationEvents();

    while (!invoked[0]) {
      UIUtil.dispatchAllInvocationEvents();
      Thread.sleep(delay);
    }
  }

  private static Date raidDate(Bombed bombed) {
    final Calendar instance = Calendar.getInstance();
    instance.set(Calendar.YEAR, bombed.year());
    instance.set(Calendar.MONTH, bombed.month());
    instance.set(Calendar.DAY_OF_MONTH, bombed.day());
    instance.set(Calendar.HOUR_OF_DAY, bombed.time());
    instance.set(Calendar.MINUTE, 0);
    Date time = instance.getTime();

    return time;
  }

  public static boolean bombExplodes(Bombed bombedAnnotation) {
    Date now = new Date();
    return now.after(raidDate(bombedAnnotation));
  }

  public static boolean bombExplodes(int year, int month, int day, int h, int m, String who, String message) {
    final Calendar instance = Calendar.getInstance();
    instance.set(Calendar.YEAR, year);
    instance.set(Calendar.MONTH, month);
    instance.set(Calendar.DAY_OF_MONTH, day);
    instance.set(Calendar.HOUR_OF_DAY, h);
    instance.set(Calendar.MINUTE, m);
    Date time = instance.getTime();
    return isItMe(who) || new Date().after(time);
  }

  public static boolean isRotten(Bombed bomb) {
    long bombRotPeriod = 30L * 24 * 60 * 60 * 1000; // month
    return new Date().after(new Date(raidDate(bomb).getTime() + bombRotPeriod));
  }

  private static boolean isItMe(final String who) {
    return Comparing.equal(who, SystemProperties.getUserName(), false);
  }
}
