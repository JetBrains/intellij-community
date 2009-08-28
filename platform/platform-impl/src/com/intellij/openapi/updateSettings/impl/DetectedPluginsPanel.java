/*
 * User: anna
 * Date: 04-Dec-2007
 */
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.OrderPanel;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import java.util.ArrayList;

public class DetectedPluginsPanel extends OrderPanel<PluginDownloader> {
  private final ArrayList<Listener> myListeners = new ArrayList<Listener>();
  private static final String AVAILABLE_VERSION = "available version: ";
  private static final String INSTALLED_VERSION = "installed version: ";

  protected DetectedPluginsPanel() {
    super(PluginDownloader.class);
    getEntryTable().setDefaultRenderer(PluginDownloader.class, new ColoredTableCellRenderer(){
      protected void customizeCellRenderer(final JTable table, final Object value, final boolean selected, final boolean hasFocus, final int row, final int column) {
        final PluginDownloader downloader = (PluginDownloader)value;
        append(downloader.getPluginName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        final IdeaPluginDescriptor ideaPluginDescriptor = PluginManager.getPlugin(PluginId.getId(downloader.getPluginId()));
        final String loadedVersion = downloader.getPluginVersion();
        if (loadedVersion != null || (ideaPluginDescriptor != null && ideaPluginDescriptor.getVersion() != null)) {
          append(" (" + (ideaPluginDescriptor != null && ideaPluginDescriptor.getVersion() != null ? INSTALLED_VERSION + ideaPluginDescriptor.getVersion() + (loadedVersion != null ? ", " : "") :"") + (loadedVersion != null ? AVAILABLE_VERSION + loadedVersion : "") + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
      }
    });
    setCheckboxColumnName("");
  }

  public String getCheckboxColumnName() {
    return "";
  }

  public boolean isCheckable(final PluginDownloader downloader) {
    return true;
  }

  public boolean isChecked(final PluginDownloader downloader) {
    return !UpdateChecker.getDisabledToUpdatePlugins().contains(downloader.getPluginId());
  }

  public void setChecked(final PluginDownloader downloader, final boolean checked) {
    if (checked) {
      UpdateChecker.getDisabledToUpdatePlugins().remove(downloader.getPluginId());
    } else {
      UpdateChecker.getDisabledToUpdatePlugins().add(downloader.getPluginId());
    }
    for (Listener listener : myListeners) {
      listener.stateChanged();
    }
  }

  public void addStateListener(Listener l) {
    myListeners.add(l);
  }

  public static interface Listener {
    void stateChanged();
  }

}