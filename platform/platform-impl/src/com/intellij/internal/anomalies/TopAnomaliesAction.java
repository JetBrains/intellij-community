// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.anomalies;

import com.intellij.internal.InternalActionsBundle;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

final class TopAnomaliesAction extends ActionGroup {

  private static final int LIMIT = 10;

  private static final class Holder {
    private static final Comparator<Pair<?, Integer>> COMPARATOR = (o1, o2) -> {
      int i = o2.getSecond() - o1.getSecond();
      if (i != 0) {
        return i;
      }
      return Integer.compare(o1.hashCode(), o2.hashCode());
    };

    private static final ResettableAction TOP_PARENTS = new ResettableAction(InternalActionsBundle.messagePointer("action.Anonymous.text.parents")) {
      final TreeSet<Pair<JComponent, Integer>> top = new TreeSet<>(COMPARATOR);
      TreeSet<Pair<JComponent, Integer>> old = new TreeSet<>(COMPARATOR);

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setText("Top " + LIMIT + " Component Parents");
      }

      @Override
      void reset() {
        top.clear();
        old.clear();
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        old = new TreeSet<>(top);
        top.clear();
        Window[] windows = Window.getWindows();
        for (Window window : windows) {
          if (window.isVisible() && (window instanceof JFrame f)) {
            checkParents((JComponent)f.getContentPane(), top, LIMIT);
          }
        }

        System.out.println("Top " + LIMIT + " component parents");
        for (Pair<JComponent, Integer> pair : top) {
          System.out.println(
            pair.first.getClass().getName() + " (" + pair.second + " children)" + getChange(old, pair.first, pair.second));
        }
      }

      private static void checkParents(JComponent component, Set<Pair<JComponent, Integer>> top, int limit) {
        top.add(Pair.create(component, component.getComponentCount()));

        trimToLimit(top, limit);

        for (int i = 0; i < component.getComponentCount(); i++) {
          Component child = component.getComponent(i);
          if (child instanceof JComponent) {
            checkParents((JComponent)child, top, limit);
          }
        }
      }
    };

    private static final ResettableAction TOP_UI_PROPERTIES = new ResettableAction(InternalActionsBundle.messagePointer("action.Anonymous.text.clientproperties")) {
      final TreeSet<Pair<JComponent, Integer>> top = new TreeSet<>(COMPARATOR);
      TreeSet<Pair<JComponent, Integer>> old = new TreeSet<>(COMPARATOR);

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setText("Top " + LIMIT + " ClientProperties");
      }

      @Override
      void reset() {
        top.clear();
        old.clear();
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        old = new TreeSet<>(top);
        top.clear();
        Window[] windows = Window.getWindows();
        for (Window window : windows) {
          if (window.isVisible() && (window instanceof JFrame f)) {
            checkClientProperties((JComponent)f.getContentPane(), top, LIMIT);
          }
        }
        System.out.println("Top " + LIMIT + " ClientProperties");
        for (Pair<JComponent, Integer> pair : top) {
          System.out
            .println(pair.first.getClass().getName() + " (" + pair.second + " properties)" + getChange(old, pair.first, pair.second));
        }
      }

      private static void checkClientProperties(JComponent component, Set<Pair<JComponent, Integer>> top, int limit) {
        try {
          Field clientProperties = JComponent.class.getDeclaredField("clientProperties");
          clientProperties.setAccessible(true);
          Object o = clientProperties.get(component);
          if (o != null) {
            Method size = o.getClass().getMethod("size");
            size.setAccessible(true);
            Object sizeResult = size.invoke(o);
            if (sizeResult instanceof Integer) {
              top.add(Pair.create(component, (Integer)sizeResult));
              trimToLimit(top, limit);
            }
          }
        }
        catch (NoSuchMethodException | NoSuchFieldException | IllegalAccessException | InvocationTargetException e) {
        }
        for (int i = 0; i < component.getComponentCount(); i++) {
          Component child = component.getComponent(i);
          if (child instanceof JComponent) {
            checkClientProperties((JComponent)child, top, limit);
          }
        }
      }
    };


    private static final ResettableAction RESET_THEM_ALL = new ResettableAction(() -> InternalActionsBundle
      .message("action.Anonymous.text.reset.statistics")) {
      @Override
      void reset() {
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        for (ResettableAction action : CHILDREN) {
          action.reset();
        }
      }
    };

    private static final ResettableAction[] CHILDREN = {TOP_PARENTS, TOP_UI_PROPERTIES, RESET_THEM_ALL};
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    return Holder.CHILDREN;
  }

  private static <K, V> void trimToLimit(Set<Pair<K, V>> top, int limit) {
    int k = 0;
    for (Iterator<Pair<K, V>> iterator = top.iterator(); iterator.hasNext(); ) {
      k++;
      iterator.next();
      if (k >= limit) {
        iterator.remove();
      }
    }
  }

  private static <K, V extends Integer> String getChange(Set<? extends Pair<K, V>> old, K key, int newResult) {
    for (Pair<K, V> oldPair : old) {
      if (oldPair.first == key) {
        int oldResult = oldPair.second.intValue();
        if (oldResult != newResult) {
          return (oldResult > newResult ? " -" : " +") + Math.abs(newResult - oldResult);
        }
        break;
      }
    }
    return "";
  }

  private abstract static class ResettableAction extends AnAction {

    protected ResettableAction(@NotNull Supplier<String> dynamicText) {
      super(dynamicText);
    }

    abstract void reset();

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
  }
}
