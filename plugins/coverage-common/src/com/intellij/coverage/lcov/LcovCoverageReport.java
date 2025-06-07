// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.lcov;

import com.intellij.util.containers.PeekableIterator;
import com.intellij.util.containers.PeekableIteratorWrapper;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class LcovCoverageReport {

  private final Map<String, List<LineHits>> myInfo = new HashMap<>();

  public @NotNull Map<String, List<LineHits>> getInfo() {
    return myInfo;
  }

  public void mergeFileReport(@NotNull String filePath, @NotNull List<LineHits> report) {
    normalizeLineHitsList(report);
    List<LineHits> result = report;
    List<LineHits> old = myInfo.get(filePath);
    if (old != null) {
      result = doMerge(old, report);
    }
    myInfo.put(filePath, result);
  }

  private static @NotNull List<LineHits> doMerge(@NotNull List<LineHits> aList, @NotNull List<LineHits> bList) {
    PeekableIterator<LineHits> ai = new PeekableIteratorWrapper<>(aList.iterator());
    PeekableIterator<LineHits> bi = new PeekableIteratorWrapper<>(bList.iterator());
    List<LineHits> out = new ArrayList<>();
    while (ai.hasNext() && bi.hasNext()) {
      final LineHits x;
      LineHits a = ai.peek();
      LineHits b = bi.peek();
      if (a.getLineNumber() < b.getLineNumber()) {
        x = ai.next();
      }
      else if (a.getLineNumber() > b.getLineNumber()) {
        x = bi.next();
      }
      else {
        a.addHits(b.getHits());
        x = a;
        ai.next();
        bi.next();
      }
      out.add(x);
    }
    addRestItems(out, ai);
    addRestItems(out, bi);
    return out;
  }

  private static <T> void addRestItems(@NotNull List<T> out, Iterator<T> iterator) {
    while (iterator.hasNext()) {
      out.add(iterator.next());
    }
  }

  private static void normalizeLineHitsList(@NotNull List<LineHits> lineHitsList) {
    makeSortedByLineNumber(lineHitsList);
    makeUniqueByLineNumber(lineHitsList);
  }

  private static void makeSortedByLineNumber(@NotNull List<LineHits> report) {
    LineHits prev = null;
    for (LineHits cur : report) {
      if (prev != null && prev.getLineNumber() > cur.getLineNumber()) {
        Collections.sort(report);
        return;
      }
      prev = cur;
    }
  }

  private static void makeUniqueByLineNumber(@NotNull List<LineHits> report) {
    boolean unique = checkForLineUniqueness(report);
    if (unique) {
      return;
    }
    List<LineHits> out = new ArrayList<>(report.size());
    LineHits prev = null;
    for (LineHits cur : report) {
      if (prev != null && prev.getLineNumber() == cur.getLineNumber()) {
        prev.addHits(cur.getHits());
      } else {
        out.add(cur);
        prev = cur;
      }
    }
    report.clear();
    report.addAll(out);
  }

  private static boolean checkForLineUniqueness(@NotNull List<LineHits> lineHitsList) {
    LineHits prev = null;
    for (LineHits cur : lineHitsList) {
      if (prev != null && prev.getLineNumber() == cur.getLineNumber()) {
        return false;
      }
      prev = cur;
    }
    return true;
  }

  public static class LineHits implements Comparable<LineHits> {
    private final int myLineNumber;
    private final String myFunctionName;
    private int myHits;

    public LineHits(int lineNumber, int hits, String functionName) {
      myLineNumber = lineNumber;
      myHits = hits;
      myFunctionName = functionName;
    }

    public int getLineNumber() {
      return myLineNumber;
    }

    public int getHits() {
      return myHits;
    }

    public String getFunctionName() {
      return myFunctionName;
    }

    @Override
    public int compareTo(LineHits o) {
      return myLineNumber - o.myLineNumber;
    }

    public void addHits(int hitCount) {
      myHits += hitCount;
    }

    @Override
    public String toString() {
      return "{" +
             "line=" + myLineNumber +
             ", hits=" + myHits +
             ", method='" + myFunctionName + '\'' +
             '}';
    }
  }
}

