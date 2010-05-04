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

import com.intellij.ide.DataManager;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.idea.Bombed;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.UIUtil;
import junit.framework.Assert;
import junit.framework.AssertionFailedError;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

/**
 * @author yole
 */
public class PlatformTestUtil {
  /**
   * Measured on dual core p4 3HZ 1gig ram
   */
  protected static final long ETALON_TIMING = 438;
  public static final boolean COVERAGE_ENABLED_BUILD = "true".equals(System.getProperty("idea.coverage.enabled.build"));

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

  protected static String toString(Object node, Queryable.PrintInfo printInfo) {
    if (node instanceof AbstractTreeNode) {
      if (printInfo != null) {
        return ((AbstractTreeNode)node).toTestString(printInfo);
      } else {
        return ((AbstractTreeNode)node).getTestPresentation();
      }
    }
    else if (node == null) {
      return "NULL";
    }
    else {
      return node.toString();
    }
  }

  public static String print(JTree tree, boolean withSelection) {
    return print(tree, withSelection, null);
  }

  public static String print(JTree tree, boolean withSelection, Condition<String> nodePrintCondition) {
    StringBuffer buffer = new StringBuffer();
    Object root = tree.getModel().getRoot();
    printImpl(tree, root, buffer, 0, withSelection, nodePrintCondition);
    return buffer.toString();
  }

  
  private static void printImpl(JTree tree, Object root, StringBuffer buffer, int level, boolean withSelection, @Nullable Condition<String> nodePrintCondition) {
    DefaultMutableTreeNode defaultMutableTreeNode = (DefaultMutableTreeNode)root;


    final Object userObject = defaultMutableTreeNode.getUserObject();
    String nodeText;
    if (userObject != null) {
      nodeText = toString(userObject, null);
    }
    else {
      nodeText = defaultMutableTreeNode + "";
    }


    if (nodePrintCondition != null && !nodePrintCondition.value(nodeText)) return;

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


    buffer.append(nodeText);

    if (withSelection && selected) {
      buffer.append("]");
    }

    buffer.append("\n");
    int childCount = tree.getModel().getChildCount(root);
    if (expanded) {
      for (int i = 0; i < childCount; i++) {
        printImpl(tree, tree.getModel().getChild(root, i), buffer, level + 1, withSelection, nodePrintCondition);
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

    return instance.getTime();
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

  /**
   * @deprecated use {@link #print(AbstractTreeStructure structure,
                                   Object node,
                                   int currentLevel,
                                   Comparator comparator,
                                   int maxRowCount,
                                   char paddingChar,
                                   String[] dumpNames)}
   */
  public static StringBuffer print(AbstractTreeStructure structure,
                                   Object node,
                                   int currentLevel,
                                   Comparator comparator,
                                   int maxRowCount,
                                   char paddingChar) {

    return print(structure, node, currentLevel, comparator, maxRowCount, paddingChar, null);
  }

  public static StringBuffer print(AbstractTreeStructure structure,
                                   Object node,
                                   int currentLevel,
                                   Comparator comparator,
                                   int maxRowCount,
                                   char paddingChar,
                                   Queryable.PrintInfo printInfo) {
    StringBuffer buffer = new StringBuffer();
    doPrint(buffer, currentLevel, node, structure, comparator, maxRowCount, 0, paddingChar, printInfo);
    return buffer;
  }

  private static int doPrint(StringBuffer buffer,
                             int currentLevel,
                             Object node,
                             AbstractTreeStructure structure,
                             Comparator comparator,
                             int maxRowCount,
                             int currentLine,
                             char paddingChar) {
    return doPrint(buffer, currentLevel, node, structure, comparator, maxRowCount, currentLine, paddingChar, null);
  }

  private static int doPrint(StringBuffer buffer,
                             int currentLevel,
                             Object node,
                             AbstractTreeStructure structure,
                             Comparator comparator,
                             int maxRowCount,
                             int currentLine,
                             char paddingChar,
                             Queryable.PrintInfo printInfo) {
    if (currentLine >= maxRowCount && maxRowCount != -1) return currentLine;

    StringUtil.repeatSymbol(buffer, paddingChar, currentLevel);
    buffer.append(toString(node, printInfo)).append("\n");
    currentLine++;
    Object[] children = structure.getChildElements(node);

    if (comparator != null) {
      ArrayList<?> list = new ArrayList<Object>(Arrays.asList(children));
      Collections.sort(list, comparator);
      children = ArrayUtil.toObjectArray(list);
    }
    for (Object child : children) {
      currentLine = doPrint(buffer, currentLevel + 1, child, structure, comparator, maxRowCount, currentLine, paddingChar, printInfo);
    }

    return currentLine;
  }

  public static String print(Object[] objects) {
    return print(Arrays.asList(objects));
  }

  public static String print(Collection c) {
    StringBuilder result = new StringBuilder();
    for (Iterator iterator = c.iterator(); iterator.hasNext();) {
      Object each = iterator.next();
      result.append(toString(each, null));
      if (iterator.hasNext()) {
        result.append("\n");
      }
    }

    return result.toString();
  }

  public static String print(ListModel model) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < model.getSize(); i++) {
      result.append(toString(model.getElementAt(i), null));
      result.append("\n");
    }
    return result.toString();
  }

  public static String print(JTree tree) {
    return print(tree, false);
  }

  public static void assertTreeStructureEquals(final AbstractTreeStructure treeStructure, final String expected) {
    Assert.assertEquals(expected, print(treeStructure, treeStructure.getRootElement(), 0, null, -1, ' ').toString());
  }

  public static void invokeNamedAction(final String actionId) {
    final AnAction action = ActionManager.getInstance().getAction(actionId);
    Assert.assertNotNull(action);
    final Presentation presentation = new Presentation();
    final AnActionEvent event =
        new AnActionEvent(null, DataManager.getInstance().getDataContext(), "", presentation, ActionManager.getInstance(), 0);
    action.update(event);
    Assert.assertTrue(presentation.isEnabled());
    action.actionPerformed(event);
  }

  public static void assertTiming(String message, long expected, long actual) {
    if (COVERAGE_ENABLED_BUILD) return;
    long expectedOnMyMachine = Math.max(1, expected * Timings.MACHINE_TIMING / ETALON_TIMING);
    final double acceptableChangeFactor = 1.1;

    // Allow 10% more in case of test machine is busy.
    // For faster machines (expectedOnMyMachine < expected) allow nonlinear performance rating:
    // just perform better than acceptable expected
    int percentage = (int)(100.0 * (actual - expectedOnMyMachine) / expectedOnMyMachine);
    String logMessage = message + "." +
                         " Operation took " + percentage + "% longer than expected." +
                         " Expected on my machine: " + expectedOnMyMachine + "." +
                         " Actual: " + actual + "." +
                         " Expected on Etalon machine: " + expected + ";" +
                         " Actual on Etalon: " + actual * ETALON_TIMING / Timings.MACHINE_TIMING;

    if (actual < expectedOnMyMachine) {
      TeamCityLogger.info(logMessage);
    }
    else if (actual > expectedOnMyMachine * acceptableChangeFactor &&
        (expectedOnMyMachine > expected || actual > expected * acceptableChangeFactor)) {
      TeamCityLogger.warning(logMessage);
    }
    else {
      TeamCityLogger.error(logMessage);
    }
  }

  public static void assertTiming(String message, long expected, @NotNull Runnable actionToMeasure) {
    assertTiming(message, expected, 4, actionToMeasure);
  }

  public static long measure(@NotNull Runnable actionToMeasure) {
    long start = System.currentTimeMillis();
    actionToMeasure.run();
    long finish = System.currentTimeMillis();
    return finish - start;
  }

  public static void assertTiming(String message, long expected, int attempts, @NotNull Runnable actionToMeasure) {
    while (true) {
      attempts--;
      long duration = measure(actionToMeasure);
      try {
        assertTiming(message, expected, duration);
        break;
      }
      catch (AssertionFailedError e) {
        if (attempts == 0) throw e;
        System.gc();
        System.gc();
        System.gc();
        TeamCityLogger.info("Another epic fail: "+e.getMessage() +"; Attempts remained: "+attempts);
      }
    }
  }
}
