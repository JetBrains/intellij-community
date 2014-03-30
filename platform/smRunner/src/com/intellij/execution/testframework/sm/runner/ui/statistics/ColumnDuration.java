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

import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.ui.TestsPresentationUtil;
import com.intellij.execution.testframework.sm.SMTestsRunnerBundle;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.util.Comparator;

/**
 * @author Roman Chernyatchik
*/
public class ColumnDuration extends BaseColumn implements Comparator<SMTestProxy> {
  public ColumnDuration() {
    super(SMTestsRunnerBundle.message("sm.test.runner.ui.tabs.statistics.columns.duration.title"));
  }

  public String valueOf(final SMTestProxy testProxy) {
    return TestsPresentationUtil.getDurationPresentation(testProxy);
  }

  @Nullable
  public Comparator<SMTestProxy> getComparator(){
    return this;
  }

  public int compare(final SMTestProxy proxy1, final SMTestProxy proxy2) {
    final Long duration1 = proxy1.getDuration();
    final Long duration2 = proxy2.getDuration();

    if (duration1 == null) {
      return duration2 == null ? 0 : -1;
    }
    if (duration2 == null) {
      return +1;
    }
    return duration1.compareTo(duration2);
  }


  @Override
  public TableCellRenderer getRenderer(final SMTestProxy proxy) {
    return new DurationCellRenderer();
  }

  public static class DurationCellRenderer extends ColoredTableCellRenderer implements ColoredRenderer {

    public DurationCellRenderer() {
    }

    public void customizeCellRenderer(final JTable table,
                                         final Object value,
                                         final boolean selected,
                                         final boolean hasFocus,
                                         final int row,
                                         final int column) {
      assert value != null;

      append(value.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }
}
