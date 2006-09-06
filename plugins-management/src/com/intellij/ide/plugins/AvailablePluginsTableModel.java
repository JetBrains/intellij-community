/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 19-Aug-2006
 * Time: 14:54:29
 */
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Dec 26, 2003
 * Time: 3:51:58 PM
 * To change this template use Options | File Templates.
 */
public class AvailablePluginsTableModel extends PluginTableModel {
  private static Map<PluginId, String> UpdateVersions = new HashMap<PluginId, String>();

  public AvailablePluginsTableModel(SortableProvider sortableProvider) {
    super(sortableProvider, 
          new PluginManagerColumnInfo(PluginManagerColumnInfo.COLUMN_NAME, sortableProvider),
          new PluginManagerColumnInfo(PluginManagerColumnInfo.COLUMN_DOWNLOADS, sortableProvider),
          new PluginManagerColumnInfo(PluginManagerColumnInfo.COLUMN_DATE, sortableProvider),
          new PluginManagerColumnInfo(PluginManagerColumnInfo.COLUMN_CATEGORY, sortableProvider));

    view = new ArrayList<IdeaPluginDescriptor>();
    sortByColumn(0);
  }

  public void addData(ArrayList<IdeaPluginDescriptor> list) {
    //  For each downloadable plugin we need to know whether its counterpart
    //  is already installed, and if yes compare the difference in versions:
    //  availability of newer versions will be indicated separately.
    for (IdeaPluginDescriptor descr : list) {
      IdeaPluginDescriptor existing = PluginManager.getPlugin(descr.getPluginId());
      if (existing == null) {
        view.add(descr);
        UpdateVersions.put(descr.getPluginId(), descr.getVersion());
      }
    }
    safeSort();
  }

  public void modifyData(ArrayList<IdeaPluginDescriptor> list) {
    //  For each downloadable plugin we need to know whether its counterpart
    //  is already installed, and if yes compare the difference in versions:
    //  availability of newer versions will be indicated separately.
    for (IdeaPluginDescriptor descr : list) {
      PluginId descrId = descr.getPluginId();
      IdeaPluginDescriptor existing = PluginManager.getPlugin(descrId);
      if (existing == null) {
        if (UpdateVersions.containsKey(descrId)) {
          String currVersion = UpdateVersions.get(descrId);
          int state = PluginManagerColumnInfo.compareVersion(descr.getVersion(), currVersion);
          if (state > 0) {
            for (int i = 0; i < view.size(); i++) {
              IdeaPluginDescriptor obsolete = view.get(i);
              if (obsolete.getPluginId() == descrId) view.remove(obsolete);
            }
            view.add(descr);
          }
        }
        else {
          view.add(descr);
        }
      }
    }
    safeSort();
  }

}