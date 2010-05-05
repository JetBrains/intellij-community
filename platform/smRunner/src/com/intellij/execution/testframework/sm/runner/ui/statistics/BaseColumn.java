/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.execution.testframework.sm.runner.ui.statistics;

import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.NullableFunction;

import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public abstract class BaseColumn extends ColumnInfo<SMTestProxy, String> {
  private final NullableFunction<List<SMTestProxy>, Object> oldSortFun =
      new NullableFunction<List<SMTestProxy>, Object>() {
        @Nullable
        public Object fun(final List<SMTestProxy> proxies) {
          BaseColumn.super.sort(proxies);

          return null;
        }
      };

  public BaseColumn(String name) {
    super(name);
  }

  @Override
  public void sort(@NotNull final List<SMTestProxy> testProxies) {
    //Invariant: comparator should left Total(initially at row = 0) row as uppermost element!
    StatisticsTableModel.applySortOperation(testProxies, oldSortFun);
  }
}
