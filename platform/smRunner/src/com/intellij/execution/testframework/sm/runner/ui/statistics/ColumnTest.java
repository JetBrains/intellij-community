package com.intellij.execution.testframework.sm.runner.ui.statistics;

import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.SMTestsRunnerBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.util.Comparator;

/**
 * @author Roman Chernyatchik
*/
public class ColumnTest extends BaseColumn implements Comparator<SMTestProxy>{
  public ColumnTest() {
    super(SMTestsRunnerBundle.message("sm.test.runner.ui.tabs.statistics.columns.test.title"));
  }

  @NotNull
  public String valueOf(final SMTestProxy testProxy) {
    return testProxy.getPresentableName();
  }

  @Nullable
  public Comparator<SMTestProxy> getComparator(){
    return this;
  }

  public int compare(final SMTestProxy proxy1, final SMTestProxy proxy2) {
    return proxy1.getName().compareTo(proxy2.getName());
  }

  @Override
  public TableCellRenderer getRenderer(final SMTestProxy proxy) {
    return new TestsCellRenderer(proxy);
  }

  public static class TestsCellRenderer extends ColoredTableCellRenderer implements ColoredRenderer {
    @NonNls private static final String TOTAL_TITLE = SMTestsRunnerBundle.message("sm.test.runner.ui.tabs.statistics.columns.test.total.title");
    @NonNls private static final String PARENT_TITLE = "..";

    private final SMTestProxy myProxy;

    public TestsCellRenderer(final SMTestProxy proxy) {
      myProxy = proxy;
    }

    public void customizeCellRenderer(final JTable table,
                                         final Object value,
                                         final boolean selected,
                                         final boolean hasFocus,
                                         final int row,
                                         final int column) {
      assert value != null;

      final String title = value.toString();
      //Black bold for with caption "Total" for parent suite of items in statistics
      if (myProxy.isSuite() && isFirstLine(row)) {
        if (myProxy.getParent() == null) {
          append(TOTAL_TITLE, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        }
        else {
          append(PARENT_TITLE, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          append(" (" + myProxy.getName() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        return;
      }
      //Black, regular for other suites and tests
      append(title, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }

    public static boolean isFirstLine(final int row) {
      return row == 0;
    }
  }
}
