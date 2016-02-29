/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.CommonBundle;
import com.intellij.ide.plugins.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.DetectedPluginsPanel;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.TableUtil;
import com.intellij.util.Function;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.*;

/**
 * @author anna
 */
public class PluginsAdvertiserDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#" + PluginsAdvertiserDialog.class.getName());

  @Nullable private final Project myProject;
  private final PluginDownloader[] myUploadedPlugins;
  private final List<PluginId> myAllPlugins;
  private final Set<String> mySkippedPlugins = new HashSet<String>();

  private final PluginHelper pluginHelper = new PluginHelper();

  PluginsAdvertiserDialog(@Nullable Project project, PluginDownloader[] plugins, List<PluginId> allPlugins) {
    super(project);
    myProject = project;
    Arrays.sort(plugins, new Comparator<PluginDownloader>() {
      @Override
      public int compare(PluginDownloader o1, PluginDownloader o2) {
        return o1.getPluginName().compareToIgnoreCase(o2.getPluginName());
      }
    });
    myUploadedPlugins = plugins;
    myAllPlugins = allPlugins;
    setTitle("Choose Plugins to Install or Enable");
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    final DetectedPluginsPanel foundPluginsPanel = new DetectedPluginsPanel() {
      @Override
      protected Set<String> getSkippedPlugins() {
        return mySkippedPlugins;
      }
    };

    for (PluginDownloader uploadedPlugin : myUploadedPlugins) {
      foundPluginsPanel.add(uploadedPlugin);
    }
    TableUtil.ensureSelectionExists(foundPluginsPanel.getEntryTable());
    return foundPluginsPanel;
  }

  @Override
  protected void doOKAction() {
    final Set<String> pluginsToEnable = new HashSet<String>();
    final List<PluginNode> nodes = new ArrayList<PluginNode>();
    for (PluginDownloader downloader : myUploadedPlugins) {
      String pluginId = downloader.getPluginId();
      if (!mySkippedPlugins.contains(pluginId)) {
        pluginsToEnable.add(pluginId);
        if (!pluginHelper.isDisabled(pluginId)) {
          final PluginNode pluginNode = PluginDownloader.createPluginNode(null, downloader);
          if (pluginNode != null) {
            nodes.add(pluginNode);
          }
        }
      }
    }

    final Set<IdeaPluginDescriptor> disabled = new HashSet<IdeaPluginDescriptor>();
    final Set<IdeaPluginDescriptor> disabledDependants = new HashSet<IdeaPluginDescriptor>();
    for (PluginNode node : nodes) {
      final PluginId pluginId = node.getPluginId();
      if (pluginHelper.isDisabled(pluginId)) {
        disabled.add(node);
      }
      final List<PluginId> depends = node.getDepends();
      if (depends != null) {
        final Set<PluginId> optionalDeps = new HashSet<PluginId>(Arrays.asList(node.getOptionalDependentPluginIds()));
        for (PluginId dependantId : depends) {
          if (optionalDeps.contains(dependantId)) continue;
          final IdeaPluginDescriptor pluginDescriptor = PluginManager.getPlugin(dependantId);
          if (pluginDescriptor != null && pluginHelper.isDisabled(dependantId)) {
            disabledDependants.add(pluginDescriptor);
          }
        }
      }
    }
    suggestToEnableInstalledPlugins(pluginHelper, disabled, disabledDependants, nodes);

    final Runnable notifyRunnable = new Runnable() {
      @Override
      public void run() {
        PluginManagerMain.notifyPluginsUpdated(myProject);
      }
    };
    for (String pluginId : pluginsToEnable) {
      PluginManagerCore.enablePlugin(pluginId);
    }
    if (!nodes.isEmpty()) {
      try {
        PluginManagerMain.downloadPlugins(nodes, myAllPlugins, notifyRunnable, null);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    else {
      if (!pluginsToEnable.isEmpty()) {
        notifyRunnable.run();
      }
    }
    super.doOKAction();
  }

  private static boolean suggestToEnableInstalledPlugins(PluginHelper pluginHelper,
                                                         Set<IdeaPluginDescriptor> disabled,
                                                         Set<IdeaPluginDescriptor> disabledDependants,
                                                         List<PluginNode> list) {
    if (!disabled.isEmpty() || !disabledDependants.isEmpty()) {
      String message = "";
      if (disabled.size() == 1) {
        message += "Updated plugin '" + disabled.iterator().next().getName() + "' is disabled.";
      }
      else if (!disabled.isEmpty()) {
        message += "Updated plugins " + StringUtil.join(disabled, new Function<IdeaPluginDescriptor, String>() {
          @Override
          public String fun(IdeaPluginDescriptor pluginDescriptor) {
            return pluginDescriptor.getName();
          }
        }, ", ") + " are disabled.";
      }

      if (!disabledDependants.isEmpty()) {
        message += "<br>";
        message += "Updated plugin" + (list.size() > 1 ? "s depend " : " depends ") + "on disabled";
        if (disabledDependants.size() == 1) {
          message += " plugin '" + disabledDependants.iterator().next().getName() + "'.";
        }
        else {
          message += " plugins " + StringUtil.join(disabledDependants, new Function<IdeaPluginDescriptor, String>() {
            @Override
            public String fun(IdeaPluginDescriptor pluginDescriptor) {
              return pluginDescriptor.getName();
            }
          }, ", ") + ".";
        }
      }
      message += " Disabled plugins " + (disabled.isEmpty() ? "and plugins which depend on disabled " :"") + "won't be activated after restart.";

      int result;
      if (!disabled.isEmpty() && !disabledDependants.isEmpty()) {
        result =
          Messages.showYesNoCancelDialog(XmlStringUtil.wrapInHtml(message), CommonBundle.getWarningTitle(), "Enable all",
                                         "Enable updated plugin" + (disabled.size() > 1 ? "s" : ""), CommonBundle.getCancelButtonText(),
                                         Messages.getQuestionIcon());
        if (result == Messages.CANCEL) return false;
      }
      else {
        message += "<br>Would you like to enable ";
        if (!disabled.isEmpty()) {
          message += "updated plugin" + (disabled.size() > 1 ? "s" : "");
        }
        else {
          //noinspection SpellCheckingInspection
          message += "plugin dependenc" + (disabledDependants.size() > 1 ? "ies" : "y");
        }
        message += "?";
        result = Messages.showYesNoDialog(XmlStringUtil.wrapInHtml(message), CommonBundle.getWarningTitle(), Messages.getQuestionIcon());
        if (result == Messages.NO) return false;
      }

      if (result == Messages.YES) {
        disabled.addAll(disabledDependants);
        pluginHelper.enablePlugins(disabled);
      }
      else if (result == Messages.NO && !disabled.isEmpty()) {
        pluginHelper.enablePlugins(disabled);
      }
      return true;
    }
    return false;
  }


  class PluginHelper {
    void enablePlugins(Set<IdeaPluginDescriptor> disabled) {
      for (IdeaPluginDescriptor descriptor : disabled) {
        PluginManagerCore.enablePlugin(descriptor.getPluginId().getIdString());
      }
    }

    boolean isDisabled(String pluginId) {
      return PluginManagerCore.getDisabledPlugins().contains(pluginId);
    }

    boolean isDisabled(PluginId pluginId) {
      return PluginManagerCore.getDisabledPlugins().contains(pluginId.getIdString());
    }

  }
}
