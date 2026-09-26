// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

/**
 * @author Konstantin Bulenkov
 */
public interface EditableModel extends ItemRemovable {
  void addRow();

  void exchangeRows(int oldIndex, int newIndex);

  boolean canExchangeRows(int oldIndex, int newIndex);
}
