// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginHeaderPanel;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.newui.MyPluginModel;
import com.intellij.ide.plugins.newui.PluginDetailsPageComponent;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.Collection;
import java.util.HashSet;

/**
 * @author anna
 */
public final class DetectedPluginsPanel extends OrderPanel<PluginDownloader> {

  private final PluginDetailsPageComponent myDetailsComponent;
  private final PluginHeaderPanel myHeader = new PluginHeaderPanel();
  private final HashSet<PluginId> mySkippedPlugins = new HashSet<>();

  public DetectedPluginsPanel(@Nullable Project project) {
    super(PluginDownloader.class);
    MyPluginModel pluginModel = new MyPluginModel(project);
    myDetailsComponent = new PluginDetailsPageComponent(pluginModel, (aSource, aLinkData) -> {}, true);
    JTable entryTable = getEntryTable();
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
        if (!(value instanceof PluginDownloader)) return;

        PluginDownloader downloader = (PluginDownloader)value;
        String pluginName = downloader.getPluginName();
        append(pluginName, SimpleTextAttributes.REGULAR_ATTRIBUTES);

        IdeaPluginDescriptor installedPlugin = PluginManagerCore.getPlugin(downloader.getId());

        String oldPluginName = installedPlugin != null ? installedPlugin.getName() : null;
        if (oldPluginName != null &&
            !Comparing.strEqual(pluginName, oldPluginName)) {
          append(" - " + oldPluginName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }

        String installedVersion = installedPlugin != null ? installedPlugin.getVersion() : null;
        String availableVersion = downloader.getPluginVersion();
        String version = installedVersion != null && availableVersion != null ?
                         StringUtil.join(new String[]{installedVersion, UIUtil.rightArrow(), availableVersion}, "") :
                         StringUtil.defaultIfEmpty(installedVersion, availableVersion);

        if (StringUtil.isNotEmpty(version)) {
          append(" " + version, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
        }
      }
    });
    entryTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        int selectedRow = entryTable.getSelectedRow();
        if (selectedRow != -1) {
          IdeaPluginDescriptor plugin = getValueAt(selectedRow).getDescriptor();
          myHeader.setPlugin(plugin);
          myDetailsComponent.setOnlyUpdateMode();
          myDetailsComponent.showPluginImpl(plugin, null);
        }
      }
    });
    removeAll();

    Splitter splitter = new OnePixelSplitter(false);
    splitter.setFirstComponent(wrapWithPane(entryTable, 1, 0));
    splitter.setSecondComponent(wrapWithPane(myDetailsComponent, 0, 1));
    add(splitter, BorderLayout.CENTER);
  }

  private static @NotNull JScrollPane wrapWithPane(@NotNull JComponent c, int left, int right) {
    JScrollPane pane = ScrollPaneFactory.createScrollPane(c);
    pane.setBorder(JBUI.Borders.customLine(OnePixelDivider.BACKGROUND, 1, left, 1, right));
    return pane;
  }

  @Override
  public void addAll(@NotNull Collection<? extends PluginDownloader> orderEntries) {
    super.addAll(orderEntries);
    TableUtil.ensureSelectionExists(getEntryTable());
  }

  @Override
  public boolean isChecked(@NotNull PluginDownloader downloader) {
    return !mySkippedPlugins.contains(downloader.getId());
  }

  @Override
  public void setChecked(@NotNull PluginDownloader downloader, boolean checked) {
    PluginId pluginId = downloader.getId();
    if (checked) {
      mySkippedPlugins.remove(pluginId);
    }
    else {
      mySkippedPlugins.add(pluginId);
    }
  }

  @Override
  public void requestFocus() {
    getEntryTable().requestFocus();
  }
}