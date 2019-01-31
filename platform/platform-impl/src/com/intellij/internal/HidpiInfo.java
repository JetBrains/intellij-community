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
package com.intellij.internal;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.function.BiFunction;

/**
 * @author tav
 */
@SuppressWarnings("ConcatenationWithEmptyString")
public class HidpiInfo extends AnAction implements DumbAware {
  private static final boolean ENABLED = UIUtil.isJreHiDPIEnabled();

  private static final String JRE_HIDPI_MODE_TEXT = "Per-monitor DPI-aware";
  private static final String JRE_HIDPI_MODE_DESC =
    "<html><span style='font-size:x-small'>When enabled, the IDE UI scaling honors per-monitor DPI.<br>" +
    (SystemInfo.isWindows ?
    "To " + (ENABLED ? "disable" : "enable") + " set the JVM option <code>-Dsun.java2d.uiScale.enabled=" +
    (ENABLED ? "false" : "true") + "</code> and restart.</span></html>" :
    "The mode can not be changed on this platform.");

  private static final String MONITOR_RESOLUTION_TEXT = "Monitor resolution";
  private static final String MONITOR_RESOLUTION_DESC =
    "<html><span style='font-size:x-small'>" +
      (ENABLED ?
    "The current monitor resolution" :
    "The main monitor resolution") +
    " (in user space).</span></html>";

  private static final String SYS_SCALE_TEXT = "Monitor scale";
  private static final String SYS_SCALE_DESC =
    "<html><span style='font-size:x-small'>" +
    (ENABLED ?
    "The current monitor scale factor" :
    "The main monitor scale factor") +
    ".</span></html>";

  private static final String USR_SCALE_TEXT = "User (IDE) scale";
  private static final String USR_SCALE_DESC =
    "<html><span style='font-size:x-small'>The global IDE scale factor" +
    (JBUI.DEBUG_USER_SCALE_FACTOR.isNotNull() ?
    ", overridden by the debug property." :
    ", derived from the main font size: <code>$LABEL_FONT_SIZE" +
    (ENABLED ? "pt" : "px") + "</code><br>" +
    "<code>" + (SystemInfo.isMac ? "Preferences " : "Settings ") +
    "> Appearance & Behaviour > Appearance > Override default font") +
    "</code></span></html>";

  @Override
  public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
    Window activeFrame = IdeFrameImpl.getActiveFrame();
    if (activeFrame == null) return;
    Rectangle bounds = activeFrame.getGraphicsConfiguration().getBounds();

    String _USR_SCALE_DESC = USR_SCALE_DESC.replace("$LABEL_FONT_SIZE", "" + UIUtil.getLabelFont().getSize());

    String[] columns = new String[] {
      "Property", "Value", "Description"
    };

    String[][] data = new String[][] {
      {JRE_HIDPI_MODE_TEXT, ENABLED ? "enabled" : "disabled", JRE_HIDPI_MODE_DESC},
      {MONITOR_RESOLUTION_TEXT, "" + bounds.width + "x" + bounds.height, MONITOR_RESOLUTION_DESC},
      {SYS_SCALE_TEXT, "" + JBUI.sysScale(activeFrame), SYS_SCALE_DESC},
      {USR_SCALE_TEXT, "" + JBUI.scale(1f), _USR_SCALE_DESC},
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
    int padding = JBUI.scale(10);

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
