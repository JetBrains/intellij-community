// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SyncDateFormat {
  private final DateFormat myDelegate;

  public SyncDateFormat(@NotNull DateFormat delegate) {
    myDelegate = delegate;
  }

  public synchronized Date parse(@NotNull String s) throws ParseException {
    return myDelegate.parse(s);
  }

  @NlsSafe
  public synchronized String format(@NotNull Date date) {
    return myDelegate.format(date);
  }

  @NlsSafe
  public synchronized String format(long time) {
    return myDelegate.format(time);
  }

  public synchronized DateFormat getDelegate() {
    return (DateFormat)myDelegate.clone();
  }

  public synchronized String toPattern() {
    if (myDelegate instanceof SimpleDateFormat) {
      return ((SimpleDateFormat)myDelegate).toPattern();
    }
    throw new UnsupportedOperationException("Delegate must be of SimpleDateFormat type");
  }
}