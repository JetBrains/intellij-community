/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.table;

import com.intellij.ui.SpeedSearchBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractList;
import java.util.ListIterator;

public class VcsLogSpeedSearch extends SpeedSearchBase<VcsLogGraphTable> {
  public VcsLogSpeedSearch(@NotNull VcsLogGraphTable component) {
    super(component);
  }

  @Override
  protected int getElementCount() {
    return myComponent.getRowCount();
  }

  @NotNull
  @Override
  protected ListIterator<Object> getElementIterator(int startingIndex) {
    return new MyRowsList().listIterator(startingIndex);
  }

  @Override
  protected int getSelectedIndex() {
    return myComponent.getSelectedRow();
  }

  @Override
  protected Object @NotNull [] getAllElements() {
    throw new UnsupportedOperationException("Getting all elements in a Log in an array is unsupported.");
  }

  @Nullable
  @Override
  protected String getElementText(@NotNull Object row) {
    return myComponent.getModel().getCommitMetadata((Integer)row).getSubject();
  }

  @Override
  protected void selectElement(@Nullable Object row, @NotNull String selectedText) {
    if (row == null) return;
    myComponent.jumpToRow((Integer)row);
  }

  private class MyRowsList extends AbstractList<Object> {
    @Override
    public int size() {
      return myComponent.getRowCount();
    }

    @Override
    public Object get(int index) {
      return index;
    }
  }
}
