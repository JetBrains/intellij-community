package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.BooleanTableCellEditor;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.SortableColumnModel;
import org.jetbrains.annotations.NonNls;

import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Dec 26, 2003
 * Time: 3:51:58 PM
 * To change this template use Options | File Templates.
 */
public class InstalledPluginsTableModel extends PluginTableModel {
  public static Map<PluginId, Integer> NewVersions2Plugins = new HashMap<PluginId, Integer>();
  private Map<PluginId, Boolean> myEnabled = new HashMap<PluginId, Boolean>();

  public InstalledPluginsTableModel(SortableProvider sortableProvider) {
    super.sortableProvider = sortableProvider;
    super.columns = new ColumnInfo[]{new EnabledPluginInfo(), new NameColumnInfo()};
    view = new ArrayList<IdeaPluginDescriptor>(Arrays.asList(PluginManager.getPlugins()));

    myEnabled.clear();
    for (IdeaPluginDescriptor ideaPluginDescriptor : view) {
      myEnabled.put(ideaPluginDescriptor.getPluginId(), ((IdeaPluginDescriptorImpl)ideaPluginDescriptor).isEnabled());
    }

    for (Iterator<IdeaPluginDescriptor> iterator = view.iterator(); iterator.hasNext();) {
      @NonNls final String s = iterator.next().getPluginId().getIdString();
      if ("com.intellij".equals(s)) iterator.remove();
    }
    sortByColumn(getNameColumn());
  }

  public static int getCheckboxColumn() {
    return 0;
  }

  public int getNameColumn() {
    return 1;
  }

  public void addData(ArrayList<IdeaPluginDescriptor> list) {
    modifyData(list);
  }

  public void modifyData(ArrayList<IdeaPluginDescriptor> list) {
    //  For each downloadable plugin we need to know whether its counterpart
    //  is already installed, and if yes compare the difference in versions:
    //  availability of newer versions will be indicated separately.
    for (IdeaPluginDescriptor descr : list) {
      PluginId descrId = descr.getPluginId();
      IdeaPluginDescriptor existing = PluginManager.getPlugin(descrId);
      if (existing != null) {
        if (descr instanceof PluginNode) {
          updateExistingPluginInfo(descr, existing);
        } else {
          view.add(descr);
        }
      }
    }
    safeSort();
  }

  public void clearData() {
    view.clear();
    NewVersions2Plugins.clear();
  }

  private static void updateExistingPluginInfo(IdeaPluginDescriptor descr, IdeaPluginDescriptor existing) {
    int state = PluginManagerColumnInfo.compareVersion(descr.getVersion(), existing.getVersion());
    if (state > 0) {
      NewVersions2Plugins.put(existing.getPluginId(), 1);
    }

    final IdeaPluginDescriptorImpl plugin = (IdeaPluginDescriptorImpl)existing;
    plugin.setDownloadsCount(descr.getDownloads());
    plugin.setVendor(descr.getVendor());
    plugin.setVendorEmail(descr.getVendorEmail());
    plugin.setVendorUrl(descr.getVendorUrl());
    plugin.setUrl(descr.getUrl());
  }

  public static boolean hasNewerVersion(PluginId descr) {
    return NewVersions2Plugins.containsKey(descr);
  }

  private class EnabledPluginInfo extends ColumnInfo<IdeaPluginDescriptorImpl, Boolean> {

    public EnabledPluginInfo() {
      super(IdeBundle.message("plugin.manager.enable.column.title"));
    }

    public Boolean valueOf(IdeaPluginDescriptorImpl ideaPluginDescriptor) {
      return myEnabled.get(ideaPluginDescriptor.getPluginId());
    }

    public boolean isCellEditable(final IdeaPluginDescriptorImpl ideaPluginDescriptor) {
      return true;
    }

    public Class getColumnClass() {
      return Boolean.class;
    }

    public TableCellEditor getEditor(final IdeaPluginDescriptorImpl o) {
      return new BooleanTableCellEditor();
    }

    public TableCellRenderer getRenderer(final IdeaPluginDescriptorImpl ideaPluginDescriptor) {
      return new BooleanTableCellRenderer();
    }

    public void setValue(final IdeaPluginDescriptorImpl ideaPluginDescriptor, final Boolean value) {
      myEnabled.put(ideaPluginDescriptor.getPluginId(), value);
    }

    public Comparator<IdeaPluginDescriptorImpl> getComparator() {
      final boolean sortDirection = (sortableProvider.getSortOrder() == SortableColumnModel.SORT_ASCENDING);
      return new Comparator<IdeaPluginDescriptorImpl>() {
        public int compare(final IdeaPluginDescriptorImpl o1, final IdeaPluginDescriptorImpl o2) {
          if (o1.isEnabled()) {
            if (o2.isEnabled()) {
              return 0;
            }
            return sortDirection ? -1 : 1;
          }
          else {
            if (!o2.isEnabled()) {
              return 0;
            }
            return sortDirection ? 1 : -1;
          }
        }
      };
    }
  }

  private class NameColumnInfo extends PluginManagerColumnInfo {
    public NameColumnInfo() {
      super(PluginManagerColumnInfo.COLUMN_NAME, sortableProvider);
    }

    public TableCellRenderer getRenderer(final IdeaPluginDescriptor ideaPluginDescriptor) {
      final DefaultTableCellRenderer cellRenderer = (DefaultTableCellRenderer)super.getRenderer(ideaPluginDescriptor);
      if (cellRenderer != null && ideaPluginDescriptor != null) {
        final IdeaPluginDescriptorImpl descriptor = (IdeaPluginDescriptorImpl)ideaPluginDescriptor;
        if (descriptor.isDeleted()) {
          cellRenderer.setIcon(IconLoader.getIcon("/actions/clean.png"));
        }
        else if (hasNewerVersion(ideaPluginDescriptor.getPluginId())) {
          cellRenderer.setIcon(IconLoader.getIcon("/nodes/pluginobsolete.png"));
        }
        else {
          cellRenderer.setIcon(IconLoader.getIcon("/nodes/plugin.png"));
        }
        if (myEnabled.get(ideaPluginDescriptor.getPluginId()).booleanValue()) {
          for (PluginId pluginId : ideaPluginDescriptor.getDependentPluginIds()) {
            if (ArrayUtil.find(ideaPluginDescriptor.getOptionalDependentPluginIds(), pluginId) == -1 &&
                !myEnabled.get(pluginId).booleanValue()) {
              cellRenderer.setForeground(Color.red);
              final IdeaPluginDescriptor plugin = PluginManager.getPlugin(pluginId);
              if (plugin != null) {
                cellRenderer.setToolTipText(IdeBundle.message("plugin.manager.tooltip.warning", plugin.getName()));
                break;
              }
            }
          }
        }
        if (PluginManager.isIncompatible(ideaPluginDescriptor)) {
          cellRenderer.setToolTipText(IdeBundle.message("plugin.manager.incompatible.tooltip.warning"));
          cellRenderer.setForeground(Color.red);
        }
      }
      return cellRenderer;
    }
  }
}
