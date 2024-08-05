// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tasks.context;

import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class ContextInfo {

  public final String name;
  public final @Nullable String comment;
  public final long date;

  public ContextInfo(String name, long date, @Nullable String comment) {
    this.name = name;
    this.date = date;
    this.comment = comment;
  }
}
