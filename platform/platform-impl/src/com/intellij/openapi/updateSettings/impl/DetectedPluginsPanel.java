// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.*;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.ui.*;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author anna
 */
public final class DetectedPluginsPanel extends OrderPanel<PluginDownloader> {

  private final JEditorPane myDescriptionPanel = new JEditorPane();
  private final PluginHeaderPanel myHeader = new PluginHeaderPanel();
  private final HashSet<PluginId> mySkippedPlugins = new HashSet<>();

  public DetectedPluginsPanel() {
    super(PluginDownloader.class);
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
          myDescriptionPanel.setText(pluginInfoUpdate(plugin));
          myDescriptionPanel.setCaretPosition(0);
        }
      }
    });
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

  private static @NotNull @Nls String pluginInfoUpdate(@NotNull IdeaPluginDescriptor descriptor) {
    StringBuilder builder = new StringBuilder(getTextPrefix());
    String description = descriptor.getDescription();
    if (!Strings.isEmptyOrSpaces(description)) {
      builder.append(description);
    }

    String changeNotes = descriptor.getChangeNotes();
    if (!Strings.isEmptyOrSpaces(changeNotes)) {
      builder.append("<h4>Change Notes</h4>")
        .append(changeNotes);
    }

    if (!descriptor.isBundled()) {
      String vendor = descriptor.getVendor();
      boolean isVendorDefined = !Strings.isEmptyOrSpaces(vendor);

      String vendorEmail = descriptor.getVendorEmail();
      boolean isVendorEmailDefined = !Strings.isEmptyOrSpaces(vendorEmail);

      String vendorUrl = descriptor.getVendorUrl();
      boolean isVendorUrlDefined = !Strings.isEmptyOrSpaces(vendorUrl);

      if (isVendorDefined || isVendorEmailDefined || isVendorUrlDefined) {
        builder.append("<h4>Vendor</h4>");

        if (isVendorDefined) {
          builder.append(vendor);
        }
        if (isVendorUrlDefined) {
          appendLink(builder, "<br>", vendorUrl, vendorUrl);
        }
        if (isVendorEmailDefined) {
          appendLink(builder, "<br>", "mailto:" + vendorUrl, vendorEmail);
        }
      }

      String pluginDescriptorUrl = descriptor.getUrl();
      PluginInfoProvider provider = PluginInfoProvider.getInstance();
      Set<PluginId> marketplacePlugins = provider.loadCachedPlugins();
      if (marketplacePlugins == null ||
          marketplacePlugins.contains(descriptor.getPluginId())) {
        if (!Strings.isEmptyOrSpaces(pluginDescriptorUrl)) {
          appendLink(builder, "<h4>Plugin homepage</h4>", pluginDescriptorUrl, pluginDescriptorUrl);
        }

        if (marketplacePlugins == null) {
          // will get the marketplace plugins ids next time
          provider.loadPlugins();
        }
      }

      if (descriptor instanceof PluginNode) {
        String size = StringUtil.defaultIfEmpty(((PluginNode)descriptor).getPresentableSize(),
                                                IdeBundle.message("plugin.info.unknown"));
        builder.append("<h4>Size</h4>")
          .append(size);
      }
    }

    @SuppressWarnings("HardCodedStringLiteral") String result = builder.append("</body></html>").toString();
    return result.trim();
  }

  private static @NotNull @NlsSafe String getTextPrefix() {
    int fontSize = JBUIScale.scale(12);
    int m1 = JBUIScale.scale(2);
    int m2 = JBUIScale.scale(5);
    return String.format(
      "<html><head>" +
      "    <style type=\"text/css\">" +
      "        p {" +
      "            font-family: Arial,serif; font-size: %dpt; margin: %dpx %dpx" +
      "        }" +
      "    </style>" +
      "</head><body style=\"font-family: Arial,serif; font-size: %dpt; margin: %dpx %dpx;\">",
      fontSize, m1, m1, fontSize, m2, m2);
  }

  private static void appendLink(@NotNull StringBuilder builder,
                                 @NotNull @NonNls String htmlPrefix,
                                 @NotNull @NonNls String url,
                                 @NotNull @NonNls String text) {
    builder.append(htmlPrefix)
      .append("<a href=\"")
      .append(url)
      .append("\">")
      .append(text)
      .append("</a>");
  }
}