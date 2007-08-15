package com.intellij.execution.junit2.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.testframework.Filter;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.states.Statistics;
import com.intellij.execution.junit2.states.TestState;
import com.intellij.rt.execution.junit.states.PoolOfTestStates;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleColoredRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TableCellState;
import com.intellij.util.ui.ColumnInfo;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.Comparator;
import java.util.List;

public abstract class TestColumnInfo extends ColumnInfo implements Comparator {
  public TestColumnInfo(final String name) {
    super(name);
  }

  public Object valueOf(final Object object) {
    return valueOfTest((TestProxy)object);
  }

  public Comparator getComparator() {
    return this;
  }

  protected abstract String valueOfTest(TestProxy test);

  private static abstract class StatisticsColumn extends TestColumnInfo {
    public StatisticsColumn(final String name) {
      super(name);
    }

    public final int compare(final Object o1, final Object o2) {
      return getAspect(getTestStatistics(o2)) - getAspect(getTestStatistics(o1));
    }

    private static Statistics getTestStatistics(final Object o1) {
      return ((TestProxy) o1).getStatistics();
    }

    public TableCellRenderer getRenderer(final Object o) {
      return new RightAlignedRenderer();
    }

    protected abstract int getAspect(Statistics statistics);
  }

  private static final ColumnInfo TEST = new TestColumnInfo(ExecutionBundle.message("junit.runing.info.test.column.name")) {
    public String valueOfTest(final TestProxy test) {
      return Formatters.printTest(test);
    }

    public TableCellRenderer getRenderer(final Object o) {
      return new TestInfoRenderer();
    }

    public int compare(final Object o1, final Object o2) {
      return getTestName(o1).compareTo(getTestName(o2));
    }

    private String getTestName(final Object o1) {
      return ((TestProxy)o1).getInfo().getName();
    }
  };
  private static final ColumnInfo TIME = new StatisticsColumn(ExecutionBundle.message("junit.runing.info.time.elapsed.column.name")){
    public String valueOfTest(final TestProxy test) {
      return Formatters.statisticsFor(test).getTime();
    }

    public int getAspect(final Statistics statistics) {
      return statistics.getTime();
    }
  };
  private static final ColumnInfo MEMORY_DELTA = new StatisticsColumn(ExecutionBundle.message("junit.runing.info.usage.delta.column.name")){
    public String valueOfTest(final TestProxy test) {
      return Formatters.statisticsFor(test).getMemoryUsageDelta();
    }

    public int getAspect(final Statistics statistics) {
      return statistics.getMemoryUsage();
    }
  };

  private static final ColumnInfo BEFORE_MEMORY = new StatisticsColumn(ExecutionBundle.message("junit.runing.info.usage.before.column.name")){
    public String valueOfTest(final TestProxy test) {
      return Formatters.statisticsFor(test).getBeforeMemory();
    }

    public int getAspect(final Statistics statistics) {
      return statistics.getBeforeMemory();
    }
  };

  private static final ColumnInfo AFTER_MEMORY = new StatisticsColumn(ExecutionBundle.message("junit.runing.info.usage.after.column.name")){
    public String valueOfTest(final TestProxy test) {
      return Formatters.statisticsFor(test).getAfterMemory();
    }

    public int getAspect(final Statistics statistics) {
      return statistics.getAfterMemory();
    }
  };

  private static final ColumnInfo CHILDREN_STATES = new TestColumnInfo(ExecutionBundle.message("junit.runing.info.results.column.name")) {
    protected String valueOfTest(final TestProxy test) {
      return "";
    }

    public int compare(final Object o1, final Object o2) {
      return defectCount(o1) - defectCount(o2);
    }

    private int defectCount(final Object o1) {
      final TestProxy test = (TestProxy)o1;
      return test.selectChildren(Filter.DEFECT).length;
    }

    public TableCellRenderer getRenderer(final Object o) {
      return new CountDefectsRenderer();
    }
  };

  public static final ColumnInfo[] COLUMN_NAMES = new ColumnInfo[]{TEST, TIME, MEMORY_DELTA, BEFORE_MEMORY, AFTER_MEMORY, CHILDREN_STATES};

  private static TestProxy getTestAtRow(final JTable table, final int row) {
    final StatisticsTable tableModel = (StatisticsTable)table.getModel();
    return tableModel.getTestAt(row);
  }

  private static class TestInfoRenderer extends ColoredTableCellRenderer {
    protected void customizeCellRenderer(final JTable table, final Object value, final boolean selected, final boolean hasFocus, final int row, final int column) {
      TestRenderer.renderTest(getTestAtRow(table, row), this);
    }
  }

  private static class RightAlignedRenderer extends DefaultTableCellRenderer {
    public Component getTableCellRendererComponent(final JTable table, final Object value,
                                                   final boolean isSelected, final boolean hasFocus, final int row, final int column) {
      super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      setHorizontalAlignment(JLabel.RIGHT);
      return this;
    }
  }

  private static class CountDefectsRenderer extends JPanel implements TableCellRenderer {
    private static final SimpleTextAttributes RUNNING_COLOR = new SimpleTextAttributes(Font.PLAIN, Color.BLACK);
    private static final SimpleTextAttributes DEFECT_ATTRIBUTE = new SimpleTextAttributes(Font.BOLD, Color.RED);

    private final SimpleColoredRenderer myCounters = new SimpleColoredRenderer();
    private final SimpleColoredRenderer myProgressIndicator = new SimpleColoredRenderer();
    private final TableCellState myCellState = new TableCellState();
    private static final SimpleTextAttributes TERMINATED_ATTRIBUTE = new SimpleTextAttributes(Font.BOLD, Color.ORANGE);

    public CountDefectsRenderer() {
      super(new BorderLayout());
      add(myProgressIndicator, BorderLayout.WEST);
      add(myCounters, BorderLayout.EAST);
    }

    public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column) {
      myCellState.collectState(table, isSelected, hasFocus, row, column);
      myCellState.updateRenderer(this);
      updateSubcomponent(myCounters);
      updateSubcomponent(myProgressIndicator);

      customizeCellRenderer(table, row);
      return this;
    }

    private void updateSubcomponent(final SimpleColoredRenderer subRenderer) {
      subRenderer.clear();
      subRenderer.setCellState(myCellState);
      myCellState.updateRenderer(subRenderer);
      subRenderer.setBorder(null);
    }

    private void customizeCellRenderer(final JTable table, final int row) {
      final StatisticsTable model = (StatisticsTable)table.getModel();
      final TestProxy test = model.getTestAt(row);
      if (test.getChildCount() == 0) {
        customizeTestCase(test);
      }
      else {
        customizeTestSuite(test);
      }
    }

    private void customizeTestSuite(final TestProxy test) {
      int running = 0;
      int passed = 0;
      int errors = 0;
      int failed = 0;
      int ignored = 0;
      final TestState suiteState = test.getState();
      final List testCases = test.getAllTests();
      for (final Object testCase : testCases) {
        final TestProxy child = (TestProxy)testCase;
        if (!child.isLeaf()) continue;
        final TestState state = child.getState();
        if (state.isInProgress()) {
          running++;
        }
        else if (state.isPassed()) {
          passed++;
        }
        else if (state.getMagnitude() == PoolOfTestStates.ERROR_INDEX) {
          errors++;
        }
        else if (state.getMagnitude() == PoolOfTestStates.IGNORED_INDEX) {
          ignored++;
        }
        else {
          failed++;
        }
      }
      if (running > 0) {
        myProgressIndicator.append(ExecutionBundle.message("junit.runing.info.left.to.run.count.tree.node", running), RUNNING_COLOR);
      }
      String separator = "";
      if (failed > 0) {
        myCounters.append(ExecutionBundle.message("junit.runing.info.failed.count.message", failed), DEFECT_ATTRIBUTE);
        separator = " ";
      }
      if (errors > 0) {
        myCounters.append(separator + ExecutionBundle.message("junit.runing.info.errors.count.message", errors), DEFECT_ATTRIBUTE);
        separator = " ";
      }
      if (ignored> 0) {
        myCounters.append(separator + ExecutionBundle.message("junit.runing.info.ignored.count.message", ignored), DEFECT_ATTRIBUTE);
        separator = " ";
      }
      if (passed > 0) {
        final Color color = suiteState.isPassed() ? TestsUIUtil.PASSED_COLOR : Color.BLACK;
        myCounters.append(separator + ExecutionBundle.message("junit.runing.info.passed.count.message", passed), new SimpleTextAttributes(Font.BOLD, color));
      }
    }

    private void customizeTestCase(final TestProxy test) {
      final TestState state = test.getState();
      if (state.isInProgress()) {
        myCounters.append(ExecutionBundle.message("junit.runing.info.running.label"), RUNNING_COLOR);
      }
      else if (state.isPassed()) {
        myCounters
          .append(ExecutionBundle.message("junit.runing.info.passed.label"), new SimpleTextAttributes(Font.BOLD, TestsUIUtil.PASSED_COLOR));
      }
      else if (state.getMagnitude() == PoolOfTestStates.ERROR_INDEX) {
        myCounters.append(ExecutionBundle.message("junit.runing.info.error.tree.node"), DEFECT_ATTRIBUTE);
      }
      else if (state.getMagnitude() == PoolOfTestStates.TERMINATED_INDEX) {
        myCounters.append(ExecutionBundle.message("junit.runing.info.terminated.label"), TERMINATED_ATTRIBUTE);
      }
      else if (state.getMagnitude() == PoolOfTestStates.IGNORED_INDEX) {
        myCounters.append(ExecutionBundle.message("junit.runing.info.ignored.label"), TERMINATED_ATTRIBUTE);
      }
      else {
        myCounters.append(ExecutionBundle.message("junit.runing.info.assertion.tree.node"), DEFECT_ATTRIBUTE);
      }
    }
  }
}
