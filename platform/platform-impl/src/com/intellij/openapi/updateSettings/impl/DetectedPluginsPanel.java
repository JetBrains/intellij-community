// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginHeaderPanel;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginManagerMain;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.List;
import java.util.Set;

/**
 * @author anna
 */
public class DetectedPluginsPanel extends OrderPanel<PluginDownloader> {
  private final List<Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private final JEditorPane myDescriptionPanel = new JEditorPane();
  private final PluginHeaderPanel myHeader;

  public DetectedPluginsPanel() {
    super(PluginDownloader.class);
    JTable entryTable = getEntryTable();
    myHeader = new PluginHeaderPanel();
    entryTable.setTableHeader(null);
    entryTable.setDefaultRenderer(PluginDownloader.class, new ColoredTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(@NotNull JTable table,
                                           Object value,
                                           boolean selected,
                                           boolean hasFocus,
                                           int row,
                                           int column) {
        setBorder(null);
        PluginDownloader downloader = (PluginDownloader)value;
        if (downloader != null) {
          String pluginName = downloader.getPluginName();
          append(pluginName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
          IdeaPluginDescriptor ideaPluginDescriptor = PluginManagerCore.getPlugin(downloader.getId());
          if (ideaPluginDescriptor != null) {
            String oldPluginName = ideaPluginDescriptor.getName();
            if (!Comparing.strEqual(pluginName, oldPluginName)) {
              append(" - " + oldPluginName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
          }
          String loadedVersion = downloader.getPluginVersion();
          if (loadedVersion != null || (ideaPluginDescriptor != null && ideaPluginDescriptor.getVersion() != null)) {
            String installedVersion = ideaPluginDescriptor != null && ideaPluginDescriptor.getVersion() != null
                                            ? ideaPluginDescriptor.getVersion() + (loadedVersion != null ? " " + UIUtil.rightArrow() + " " : "")
                                            : "";
            String availableVersion = loadedVersion != null ? loadedVersion : "";
            append(" " + installedVersion + availableVersion, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
          }
        }
      }
    });
    entryTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        int selectedRow = entryTable.getSelectedRow();
        if (selectedRow != -1) {
          PluginDownloader selection = getValueAt(selectedRow);
          IdeaPluginDescriptor descriptor = selection.getDescriptor();
          PluginManagerMain.pluginInfoUpdate(descriptor, null, myDescriptionPanel, myHeader);
        }
      }
    });
    setCheckboxColumnName("");
    myDescriptionPanel.setPreferredSize(new JBDimension(600, 400));
    myDescriptionPanel.setEditable(false);
    myDescriptionPanel.setEditorKit(UIUtil.getHTMLEditorKit());
    myDescriptionPanel.addHyperlinkListener(new PluginManagerMain.MyHyperlinkListener());
    removeAll();

    Splitter splitter = new OnePixelSplitter(false);
    splitter.setFirstComponent(wrapWithPane(entryTable, 1, 0));
    splitter.setSecondComponent(wrapWithPane(myDescriptionPanel, 0, 1));
    add(splitter, BorderLayout.CENTER);
  }

  @NotNull
  private static JScrollPane wrapWithPane(@NotNull JComponent c, int left, int right) {
    JScrollPane pane = ScrollPaneFactory.createScrollPane(c);
    pane.setBorder(JBUI.Borders.customLine(OnePixelDivider.BACKGROUND, 1, left, 1, right));
    return pane;
  }

  @Override
  public String getCheckboxColumnName() {
    return "";
  }

  @Override
  public boolean isCheckable(PluginDownloader downloader) {
    return true;
  }

  @Override
  public boolean isChecked(PluginDownloader downloader) {
    return !getSkippedPlugins().contains(downloader.getId());
  }

  @Override
  public void setChecked(PluginDownloader downloader, boolean checked) {
    if (checked) {
      getSkippedPlugins().remove(downloader.getId());
    }
    else {
      getSkippedPlugins().add(downloader.getId());
    }
    for (Listener listener : myListeners) {
      listener.stateChanged();
    }
  }

  protected Set<PluginId> getSkippedPlugins() {
    return UpdateChecker.getDisabledToUpdate();
  }

  public void addStateListener(Listener l) {
    myListeners.add(l);
  }

  public interface Listener {
    void stateChanged();
  }
}