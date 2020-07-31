// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginManagerMain;
import com.intellij.ide.plugins.newui.*;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Divider;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.util.LineSeparator;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.*;

/**
 * @author Alexander Lobas
 */
public class PluginUpdateDialog extends DialogWrapper {
  private final Collection<PluginDownloader> myDownloaders;

  private final MyPluginModel myPluginModel;
  private final PluginsGroupComponent myPluginsPanel;
  private final PluginsGroup myGroup;
  private final PluginDetailsPageComponent myDetailsPage;

  private final Action myIgnoreAction;

  public PluginUpdateDialog(@NotNull Collection<PluginDownloader> updatedPlugins,
                            @NotNull Collection<IdeaPluginDescriptor> customRepositoryPlugins) {
    super(true);
    setTitle(IdeBundle.message("dialog.title.plugin.updates"));

    myDownloaders = updatedPlugins;

    myIgnoreAction = new AbstractAction(
      IdeBundle.message(updatedPlugins.size() == 1 ? "updates.ignore.update.button" : "updates.ignore.updates.button")) {
      @Override
      public void actionPerformed(ActionEvent e) {
        close(CANCEL_EXIT_CODE);
        ignorePlugins(ContainerUtil.map(myGroup.ui.plugins, component -> component.myUpdateDescriptor));
      }
    };

    myPluginModel = new MyPluginModel() {
      @Override
      public void runRestartButton(@NotNull Component component) {
        doOKAction();
      }

      @Override
      @NotNull
      protected Collection<IdeaPluginDescriptor> getCustomRepoPlugins() {
        return customRepositoryPlugins;
      }
    };

    myPluginModel.setTopController(Configurable.TopComponentController.EMPTY);
    myPluginModel.setPluginUpdatesService(new PluginUpdatesService() {
      @Override
      public void finishUpdate(@NotNull IdeaPluginDescriptor descriptor) {
        updateButtons();
      }

      @Override
      public void finishUpdate() {
        updateButtons();
      }
    });

    myDetailsPage = new PluginDetailsPageComponent(myPluginModel, emptyListener(), false) {
      @Override
      public void showProgress() {
      }
    };
    myDetailsPage.setOnlyUpdateMode();

    MultiSelectionEventHandler eventHandler = new MultiSelectionEventHandler();

    myPluginsPanel = new PluginsGroupComponent(new PluginListLayout(), eventHandler, descriptor -> createListComponent(descriptor));
    PluginManagerConfigurable.registerCopyProvider(myPluginsPanel);
    myPluginsPanel.setSelectionListener(__ -> {
      List<ListPluginComponent> selection = myPluginsPanel.getSelection();
      int size = selection.size();
      myDetailsPage.showPlugin(size == 1 ? selection.get(0) : null, size > 1);
    });

    myGroup = new PluginsGroup(IdeBundle.message("title.plugin.updates.available"));
    for (PluginDownloader plugin : updatedPlugins) {
      myGroup.descriptors.add(plugin.getDescriptor());
    }
    myGroup.sortByName();
    myPluginsPanel.addGroup(myGroup);

    setOKButtonText(false, false);
    setCancelButtonText(IdeBundle.message("updates.remind.later.button"));
    init();

    JRootPane rootPane = getPeer().getRootPane();
    if (rootPane != null) {
      rootPane.setPreferredSize(new JBDimension(800, 600));
    }
  }

  private void updateButtons() {
    int count = myGroup.ui.plugins.size();
    int restart = 0;
    int progress = 0;
    int updatedWithoutRestart = 0;
    for (ListPluginComponent plugin : myGroup.ui.plugins) {
      if (plugin.isRestartEnabled()) {
        restart++;
      }
      else if (plugin.underProgress()) {
        progress++;
      }
      else if (plugin.isUpdatedWithoutRestart()) {
        updatedWithoutRestart++;
      }
    }

    setOKButtonText(restart + progress > 0, updatedWithoutRestart == count);
    getCancelAction().setEnabled(restart + updatedWithoutRestart < count);
    myIgnoreAction.setEnabled(restart + progress + updatedWithoutRestart == 0);
  }

  private void setOKButtonText(boolean restart, boolean close) {
    if (close) {
      setOKButtonText(CommonBundle.getCloseButtonText());
    }
    else {
      setOKButtonText(IdeBundle.message("button.text.ide.restart.shutdown", restart ? 0 : 1,
                                        ApplicationManager.getApplication().isRestartCapable() ? 0 : 1));
    }
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();

    List<PluginDownloader> toDownloads = new ArrayList<>();
    List<IdeaPluginDescriptor> toIgnore = new ArrayList<>();
    int index = 0;
    boolean restart = false;

    for (PluginDownloader downloader : myDownloaders) {
      ListPluginComponent component = myGroup.ui.plugins.get(index++);
      if (component.isRestartEnabled() || component.underProgress()) {
        restart = true;
      }
      else if (!component.isUpdatedWithoutRestart()) {
        toDownloads.add(downloader);
        toIgnore.add(component.myUpdateDescriptor);
      }
    }

    boolean background = myPluginModel.toBackground();

    if (toDownloads.size() != myDownloaders.size() || background) {
      if (!toIgnore.isEmpty()) {
        ignorePlugins(toIgnore);
      }
      if (!background && restart) {
        ApplicationManager.getApplication().invokeLater(() -> ApplicationManagerEx.getApplicationEx().restart(true));
      }
      return;
    }

    runUpdateAll(toDownloads, getContentPanel());
  }

  public static void runUpdateAll(@NotNull Collection<PluginDownloader> toDownloads, @Nullable JComponent ownerComponent) {
    new Task.Backgroundable(null, IdeBundle.message("update.notifications.title"), true, PerformInBackgroundOption.DEAF) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        List<PluginDownloader> downloaders = UpdateInstaller.downloadPluginUpdates(toDownloads, indicator);
        if (!downloaders.isEmpty()) {
          ApplicationManager.getApplication().invokeLater(() -> {
            PluginUpdateResult result = UpdateInstaller.installDownloadedPluginUpdates(downloaders, ownerComponent, true);
            if (result.getPluginsInstalled().size() > 0) {
              if (result.getRestartRequired()) {
                if (WelcomeFrame.getInstance() == null) {
                  PluginManagerMain.notifyPluginsUpdated(null);
                }
                else {
                  PluginManagerConfigurable.shutdownOrRestartApp();
                }
              }
              else {
                String message;
                if (result.getPluginsInstalled().size() == 1) {
                  final IdeaPluginDescriptor installedPlugin = result.getPluginsInstalled().get(0);
                  message = "Updated " + installedPlugin.getName() + " plugin to version " + installedPlugin.getVersion();
                }
                else {
                  message = "Updated " + result.getPluginsInstalled() + " plugins";
                }
                UpdateChecker.NOTIFICATIONS.createNotification(message, NotificationType.INFORMATION).notify(myProject);
              }
            }
          });
        }
      }
    }.queue();
  }

  @Override
  public void doCancelAction() {
    close(CANCEL_EXIT_CODE);

    if (myPluginModel.toBackground()) {
      return;
    }

    for (ListPluginComponent plugin : myGroup.ui.plugins) {
      if (plugin.isRestartEnabled()) {
        ApplicationManager.getApplication().invokeLater(() -> PluginManagerConfigurable.shutdownOrRestartApp());
        return;
      }
    }
  }

  @Override
  protected Action @NotNull [] createLeftSideActions() {
    return ContainerUtil.ar(myIgnoreAction);
  }

  @NotNull
  @Override
  protected DialogStyle getStyle() {
    return DialogStyle.COMPACT;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.openapi.updateSettings.impl.PluginUpdateInfoDialog";
  }

  @NotNull
  private ListPluginComponent createListComponent(IdeaPluginDescriptor updateDescriptor) {
    IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(updateDescriptor.getPluginId());
    assert descriptor != null : updateDescriptor;
    ListPluginComponent component = new ListPluginComponent(myPluginModel, descriptor, emptyListener(), false) {
      @Override
      public void updateErrors() {
      }

      @Override
      public void showProgress() {
        super.showProgress();
        updateButtons();
      }
    };
    component.setOnlyUpdateMode(updateDescriptor);
    return component;
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    OnePixelSplitter splitter = new OnePixelSplitter(false, 0.45f) {
      @Override
      protected Divider createDivider() {
        Divider divider = super.createDivider();
        divider.setBackground(PluginManagerConfigurable.SEARCH_FIELD_BORDER_COLOR);
        return divider;
      }
    };

    JPanel leftPanel = new JPanel(new BorderLayout());
    leftPanel.add(PluginManagerConfigurable.createScrollPane(myPluginsPanel, true));

    OpaquePanel titlePanel = new OpaquePanel(new BorderLayout(), PluginManagerConfigurable.MAIN_BG_COLOR);
    titlePanel.setBorder(JBUI.Borders.empty(6, 10));
    leftPanel.add(titlePanel, BorderLayout.SOUTH);

    JLabel titleComponent = new JLabel(IdeBundle.message("label.plugins.can.be.updated.later.in.0.plugins", CommonBundle.settingsTitle()));
    titleComponent.setForeground(PluginsGroupComponent.SECTION_HEADER_FOREGROUND);
    titlePanel.add(titleComponent);

    ((JComponent)myGroup.ui.panel).setBorder(JBUI.Borders.empty(6, 10));

    splitter.setFirstComponent(leftPanel);
    splitter.setSecondComponent(myDetailsPage);

    return splitter;
  }

  private static Set<String> myIgnoredPluginsWithVersions;

  @NotNull
  private static File getDisabledUpdateFile() {
    return new File(PathManager.getConfigPath(), "plugin_disabled_updates.txt");
  }

  @NotNull
  private static Set<String> getIgnoredPlugins() {
    if (myIgnoredPluginsWithVersions == null) {
      myIgnoredPluginsWithVersions = new HashSet<>();

      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        try {
          File file = getDisabledUpdateFile();
          if (file.isFile()) {
            myIgnoredPluginsWithVersions.addAll(FileUtil.loadLines(file));
          }
        }
        catch (IOException e) {
          Logger.getInstance(UpdateChecker.class).error(e);
        }
      }
    }
    return myIgnoredPluginsWithVersions;
  }

  static void ignorePlugins(@NotNull List<IdeaPluginDescriptor> descriptors) {
    Set<String> ignoredPlugins = getIgnoredPlugins();

    for (IdeaPluginDescriptor descriptor : descriptors) {
      ignoredPlugins.add(getIdVersionValue(descriptor));
    }

    try {
      FileUtil
        .writeToFile(getDisabledUpdateFile(), StringUtil.join(ignoredPlugins, LineSeparator.getSystemLineSeparator().getSeparatorString()));
    }
    catch (IOException e) {
      Logger.getInstance(UpdateChecker.class).error(e);
    }
  }

  public static boolean isIgnored(@NotNull IdeaPluginDescriptor descriptor) {
    Set<String> plugins = getIgnoredPlugins();
    if (plugins.isEmpty()) {
      return false;
    }
    return plugins.contains(getIdVersionValue(descriptor));
  }

  @NotNull
  private static String getIdVersionValue(@NotNull IdeaPluginDescriptor descriptor) {
    return descriptor.getPluginId().getIdString() + "+" + descriptor.getVersion();
  }

  @NotNull
  private static <T> LinkListener<T> emptyListener() {
    return (__, ___) -> {
    };
  }
}