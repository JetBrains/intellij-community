// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.JreHiDpiUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.util.function.BiFunction;

/**
 * @author tav
 */
@SuppressWarnings("ConcatenationWithEmptyString")
public final class HidpiInfo extends AnAction implements DumbAware {
  private static final boolean ENABLED = JreHiDpiUtil.isJreHiDPIEnabled();

  private static final String JRE_HIDPI_MODE_TEXT = "Per-monitor DPI-aware";

  private static final String MONITOR_RESOLUTION_TEXT = "Monitor resolution";

  private static final String SYS_SCALE_TEXT = "Monitor scale";

  private static final String USR_SCALE_TEXT = "User (IDE) scale";

  // must be not constant to avoid call to JBUIScale
  public static String getUsrScaleDesc() {
    return "<html><span style='font-size:x-small'>The global IDE scale factor" +
           (JBUIScale.DEBUG_USER_SCALE_FACTOR.isNotNull() ?
    ", overridden by the debug property." :
     ", derived from the main font size: <code>$LABEL_FONT_SIZE" +
     (ENABLED ? "pt" : "px") + "</code><br>" +
     "<code>" + (SystemInfo.isMac ? "Preferences " : "Settings ") +
     "> Appearance & Behaviour > Appearance > Override default font") +
           "</code></span></html>";
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
    Window activeFrame = IdeFrameImpl.getActiveFrame();
    if (activeFrame == null) return;
    Rectangle bounds = activeFrame.getGraphicsConfiguration().getBounds();

    String _USR_SCALE_DESC = getUsrScaleDesc().replace("$LABEL_FONT_SIZE", "" + StartupUiUtil.getLabelFont().getSize());

    String[] columns = new String[] {
      "Property", "Value", "Description"
    };

    String[][] data = new String[][] {
      {JRE_HIDPI_MODE_TEXT, ENABLED ? "enabled" : "disabled",
        "<html><span style='font-size:x-small'>When enabled, the IDE UI scaling honors per-monitor DPI.<br>" +
            (SystemInfo.isWindows ?
        "To " + (ENABLED ? "disable" : "enable") + " set the JVM option <code>-Dsun.java2d.uiScale.enabled=" +
        (ENABLED ? "false" : "true") + "</code> and restart.</span></html>" :
         "The mode can not be changed on this platform.")},
      {MONITOR_RESOLUTION_TEXT, "" + bounds.width + "x" + bounds.height, "<html><span style='font-size:x-small'>" +
                                                                           (ENABLED ?
      "The current monitor resolution" :
      "The main monitor resolution") +
                                                                           " (in user space).</span></html>"},
      {SYS_SCALE_TEXT, "" + JBUIScale.sysScale(activeFrame), "<html><span style='font-size:x-small'>" +
                                                               (ENABLED ?
      "The current monitor scale factor" :
      "The main monitor scale factor") +
                                                               ".</span></html>"},
      {USR_SCALE_TEXT, "" + JBUIScale.scale(1f), _USR_SCALE_DESC},
    };

    JTable table = new JTable(data, columns) {
      @Override
      public TableCellRenderer getCellRenderer(int row, int column) {
        return (table1, value, isSelected, hasFocus, row1, column1) -> label(data[row1][column1]);
      }

      @Override
      public boolean isCellEditable(int row, int column) {
        return false;
      }
    };

    BiFunction<Integer, Integer, Dimension> size = (row, col) -> label(data[row][col]).getPreferredSize();
    int padding = JBUIScale.scale(10);

    table.setColumnSelectionAllowed(true);
    TableColumnModel tcm = table.getColumnModel();
    tcm.getColumn(0).setPreferredWidth(size.apply(0, 0).width + padding);
    tcm.getColumn(1).setPreferredWidth(size.apply(1, 1).width + padding);
    tcm.getColumn(2).setPreferredWidth(size.apply(0, 2).width + padding);
    table.setRowHeight(0, size.apply(0, 2).height);
    table.setRowHeight(1, size.apply(0, 2).height);
    table.setRowHeight(2, size.apply(0, 2).height);
    table.setRowHeight(3, size.apply(3, 2).height);

    JPanel tablePanel = new JPanel(new BorderLayout());
    tablePanel.add(table, BorderLayout.CENTER);
    tablePanel.add(table.getTableHeader(), BorderLayout.NORTH);

    JBPopupFactory.getInstance().createComponentPopupBuilder(tablePanel, null).
      setTitle("HiDPI Info").
      createPopup().showInCenterOf(activeFrame);
  }

  private static JLabel label(String text) {
    JLabel label = new JLabel(text);
    label.setBorder(JBUI.Borders.empty(2));
    return label;
  }
}
