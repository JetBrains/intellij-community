// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.memory.utils;

import com.intellij.openapi.util.NlsContexts;

public abstract class AbstractTableColumnDescriptor implements AbstractTableModelWithColumns.TableColumnDescriptor {
  private final @NlsContexts.ColumnName String myName;
  private final Class<?> myClass;
  protected AbstractTableColumnDescriptor(@NlsContexts.ColumnName String name, Class<?> elementClass) {
    myName = name;
    myClass = elementClass;
  }

  @Override
  public Class<?> getColumnClass() {
    return myClass;
  }


  @Override
  public String getName() {
    return myName;
  }
}
