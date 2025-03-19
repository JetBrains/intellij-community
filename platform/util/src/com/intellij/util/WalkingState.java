// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public class WalkingState<T> {
  public interface TreeGuide<T> {
    T getNextSibling(@NotNull T element);

    T getPrevSibling(@NotNull T element);

    T getFirstChild(@NotNull T element);

    T getParent(@NotNull T element);
  }

  private boolean isDown;
  protected boolean startedWalking;
  private final TreeGuide<T> myWalker;
  private boolean stopped;

  private static boolean isUnitTestMode = false;

  public void elementFinished(@NotNull T element) { }

  public WalkingState(@NotNull TreeGuide<T> delegate) {
    myWalker = delegate;
  }

  public void visit(@NotNull T element) {
    elementStarted(element);
  }

  public void elementStarted(@NotNull T element) {
    isDown = true;
    if (!startedWalking) {
      startedWalking = true;
      try {
        walkChildren(element);
      }
      finally {
        startedWalking = false;
      }
    }
  }

  private void walkChildren(@NotNull T root) {
    for (T element = next(root, root, isDown); element != null && !stopped; element = next(element, root, isDown)) {
      isDown = false; // if client visitor did not call default visitElement it means skip subtree
      if (isUnitTestMode) {
        T parent = myWalker.getParent(element);
        T next = myWalker.getNextSibling(element);
        visit(element);
        assert myWalker.getNextSibling(element) == next
          : "Next sibling of the element '" + element + "' changed. Was: " + next + "; " +
            "Now:" + myWalker.getNextSibling(element) + "; Root:" + root;
        assert myWalker.getParent(element) == parent
          : "Parent of the element '" + element + "' changed. Was: " + parent + "; " +
            "Now:" + myWalker.getParent(element) + "; Root:" + root;
      }
      else {
        visit(element);
      }
    }
  }

  public T next(T element, @NotNull T root, boolean isDown) {
    if (isDown) {
      T child = myWalker.getFirstChild(element);
      if (child != null) return child;
    }
    // up
    while (element != root && element != null) {
      T next = myWalker.getNextSibling(element);

      elementFinished(element);
      if (next != null) {
        Object nextPrev = myWalker.getPrevSibling(next);
        if (nextPrev != element) {
          String msg = "Element: " + element + "; next: " + next + "; next.prev: " + nextPrev;
          while (true) {
            T top = myWalker.getParent(element);
            if (top == null || top == root) break;
            element = top;
          }
          assert false : msg + " Top:" + element;
        }
        return next;
      }
      element = myWalker.getParent(element);
    }
    if (element != null) {
      elementFinished(element);
    }
    return null;
  }

  public void startedWalking() {
    startedWalking = true;
  }

  public void stopWalking() {
    stopped = true;
  }

  /**
   * process in the in-order fashion
   */
  public static <T> boolean processAll(@NotNull T root, @NotNull TreeGuide<T> treeGuide, final @NotNull Processor<? super T> processor) {
    final boolean[] result = {true};
    new WalkingState<T>(treeGuide) {
      @Override
      public void visit(@NotNull T element) {
        if (!processor.process(element)) {
          stopWalking();
          result[0] = false;
        }
        else {
          super.visit(element);
        }
      }
    }.visit(root);
    return result[0];
  }

  @TestOnly
  public static void setUnitTestMode() {
    isUnitTestMode = true;
  }
}
