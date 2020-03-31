package com.intellij.jps.cache.ui;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SegmentedProgressIndicatorManager {
  private static final LinkedHashMap<SubTaskProgressIndicator, String> myText2Stack = new LinkedHashMap<>();
  private static final LinkedHashMap<Object, String> myTextStack = new LinkedHashMap<>();
  private static final Object myLock = new Object();
  private final ProgressIndicator myProgressIndicator;
  private final double mySegmentSize;
  private final int myTasksCount;

  public SegmentedProgressIndicatorManager(ProgressIndicator progressIndicator, int tasksCount, double segmentSize) {
    myProgressIndicator = progressIndicator;
    mySegmentSize = segmentSize;
    myTasksCount = tasksCount;
    myText2Stack.clear();
    myTextStack.clear();
  }

  public SubTaskProgressIndicator createSubTaskIndicator() {
    assert myTasksCount != 0;
    return new SubTaskProgressIndicator(this);
  }

  public void updateFraction(double value) {
    double fractionValue = value / myTasksCount * mySegmentSize;
    synchronized (myProgressIndicator) {
      myProgressIndicator.setFraction(myProgressIndicator.getFraction() + fractionValue);
    }
  }

  public void setText(@NotNull Object obj, @Nullable String text) {
    if (text != null) {
      synchronized (myLock) {
        myTextStack.put(obj, text);
      }
      myProgressIndicator.setText(text);
    }
    else {
      String prev;
      synchronized (myLock) {
        myTextStack.remove(obj);
        prev = myTextStack.getLastValue();
      }
      if (prev != null) {
        myProgressIndicator.setText(prev);
      }
    }
  }

  public void setText2(@NotNull SubTaskProgressIndicator subTask, @Nullable String text) {
    if (text != null) {
      synchronized (myLock) {
        myText2Stack.put(subTask, text);
      }
      myProgressIndicator.setText2(text);
    }
    else {
      String prev;
      synchronized (myLock) {
        myText2Stack.remove(subTask);
        prev = myText2Stack.getLastValue();
      }
      if (prev != null) {
        myProgressIndicator.setText2(prev);
      }
    }
  }

  public void finished(Object obj) {
    setText(obj, null);
  }

  public ProgressIndicator getProgressIndicator() {
    return myProgressIndicator;
  }

  public static class SubTaskProgressIndicator extends ProgressWrapper {
    private final SegmentedProgressIndicatorManager myProgressManager;
    private double myFraction;

    private SubTaskProgressIndicator(SegmentedProgressIndicatorManager progressManager) {
      super(progressManager.myProgressIndicator, true);
      myProgressManager = progressManager;
      myFraction = 0;
    }

    @Override
    public void setFraction(double newValue) {
      double diffFraction = newValue - myFraction;
      myProgressManager.updateFraction(diffFraction);
      myFraction = newValue;
    }

    @Override
    public void setText2(String text) {
      myProgressManager.setText2(this, text);
    }

    @Override
    public void setText(String text) {
      myProgressManager.setText(this, text);
    }

    @Override
    public double getFraction() {
      return myFraction;
    }

    public void finished() {
      setFraction(1);
      myProgressManager.setText2(this, null);
    }
  }
}
