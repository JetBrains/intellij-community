// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.util;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public abstract class Column<T> {

  private final @NlsContexts.ColumnName String myName;

  public Column(@NlsContexts.ColumnName String name) {
    myName = name;
  }

  public @NlsContexts.ColumnName String getName() {
    return myName;
  }

  public Class<?> getValueClass() {
    return String.class;
  }

  public boolean isEditable() {
    return false;
  }

  public void setColumnValue(T row, Object value) {
    throw new UnsupportedOperationException();
  }

  public boolean needPack() {
    return false;
  }

  public abstract Object getColumnValue(T row);
}
