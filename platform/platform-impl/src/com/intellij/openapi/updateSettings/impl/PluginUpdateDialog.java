// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.SettingsEntryPointAction;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.PluginManagerMain;
import com.intellij.ide.plugins.PluginNode;
import com.intellij.ide.plugins.newui.*;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Divider;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.LineSeparator;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.*;

/**
 * @author Alexander Lobas
 */
public class PluginUpdateDialog extends DialogWrapper {
  private final Collection<? extends PluginDownloader> myDownloaders;

  private final MyPluginModel myPluginModel;
  private final PluginsGroupComponent myPluginsPanel;
  private final PluginsGroup myGroup = new PluginsGroup("");
  private final PluginDetailsPageComponent myDetailsPage;
  private final JLabel myTotalLabel = new JLabel();

  private final ActionLink myIgnoreAction;

  private Runnable myFinishCallback;

  public PluginUpdateDialog(@Nullable Project project,
                            @NotNull Collection<? extends PluginDownloader> updatedPlugins,
                            @Nullable Collection<? extends IdeaPluginDescriptor> customRepositoryPlugins) {
    super(true);
    setTitle(IdeBundle.message("dialog.title.plugin.updates"));

    myDownloaders = updatedPlugins;

    myIgnoreAction = new ActionLink(IdeBundle.message("updates.ignore.updates.button", updatedPlugins.size()), e -> {
      close(CANCEL_EXIT_CODE);
      ignorePlugins(ContainerUtil.map(myGroup.ui.plugins, component -> component.getPluginDescriptor()));
    });

    myPluginModel = new MyPluginModel(project) {
      @Override
      public void runRestartButton(@NotNull Component component) {
        doOKAction();
      }

      @Override
      @NotNull
      protected Collection<IdeaPluginDescriptor> getCustomRepoPlugins() {
        return customRepositoryPlugins == null ? super.getCustomRepoPlugins() : Collections.unmodifiableCollection(customRepositoryPlugins);
      }
    };

    myPluginModel.setTopController(Configurable.TopComponentController.EMPTY);
    myPluginModel.setPluginUpdatesService(new PluginUpdatesService() {
      @Override
      public void finishUpdate() {
      }
    });

    //noinspection unchecked
    myDetailsPage = new PluginDetailsPageComponent(myPluginModel, LinkListener.NULL, true);
    myDetailsPage.setOnlyUpdateMode();

    MultiSelectionEventHandler eventHandler = new MultiSelectionEventHandler();

    myPluginsPanel = new PluginsGroupComponent(new PluginListLayout(), eventHandler, descriptor -> createListComponent(descriptor));
    PluginManagerConfigurable.registerCopyProvider(myPluginsPanel);
    myPluginsPanel.setSelectionListener(__ -> {
      List<ListPluginComponent> selection = myPluginsPanel.getSelection();
      int size = selection.size();
      myDetailsPage.showPlugin(size == 1 ? selection.get(0) : null, size > 1);
    });

    for (PluginDownloader plugin : updatedPlugins) {
      myGroup.descriptors.add(plugin.getDescriptor());
    }
    myGroup.sortByName();
    myPluginsPanel.addGroup(myGroup);

    setOKButtonText(IdeBundle.message("plugins.configurable.update.button"));
    updateButtons();
    init();

    JRootPane rootPane = getPeer().getRootPane();
    if (rootPane != null) {
      rootPane.setPreferredSize(new JBDimension(800, 600));
    }
  }

  private void updateButtons() {
    long total = 0;
    int count = 0;

    for (ListPluginComponent plugin : myGroup.ui.plugins) {
      if (plugin.getChooseUpdateButton().isSelected()) {
        count++;
        try {
          total += Long.parseLong(((PluginNode)plugin.getPluginDescriptor()).getSize());
        }
        catch (NumberFormatException ignore) {
        }
      }
    }

    String text = null;
    if (total > 0) {
      text = IdeBundle.message("plugin.update.dialog.total.label", StringUtilRt.formatFileSize(total).toUpperCase(Locale.ENGLISH));
    }

    myTotalLabel.setText(text);
    getOKAction().setEnabled(count > 0);
  }

  public void setFinishCallback(@NotNull Runnable finishCallback) {
    myFinishCallback = finishCallback;
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();

    List<PluginDownloader> toDownloads = new ArrayList<>();
    int index = 0;

    for (PluginDownloader downloader : myDownloaders) {
      ListPluginComponent component = myGroup.ui.plugins.get(index++);
      if (component.getChooseUpdateButton().isSelected()) {
        toDownloads.add(downloader);
      }
    }

    runUpdateAll(toDownloads, getContentPanel(), myFinishCallback);
  }

  public static void runUpdateAll(@NotNull Collection<PluginDownloader> toDownloads,
                                  @Nullable JComponent ownerComponent,
                                  @Nullable Runnable finishCallback) {
    String message = IdeBundle.message("updates.notification.title", ApplicationNamesInfo.getInstance().getFullProductName());
    new Task.Backgroundable(null, message, true, PerformInBackgroundOption.DEAF) {
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
                String message = PluginUpdateInfoDialog.notificationText(result);
                UpdateChecker.getNotificationGroup().createNotification(message, NotificationType.INFORMATION).notify(myProject);
              }
            }
          });
        }
      }

      @Override
      public void onFinished() {
        if (finishCallback != null) {
          finishCallback.run();
        }
      }
    }.queue();
  }

  @Override
  public void doCancelAction() {
    close(CANCEL_EXIT_CODE);
  }

  @Override
  protected @Nullable JPanel createSouthAdditionalPanel() {
    JPanel panel = new Wrapper(myIgnoreAction);
    panel.setBorder(JBUI.Borders.emptyLeft(10));
    return panel;
  }

  @Override
  protected @NonNls @Nullable String getHelpId() {
    return "plugin.update.dialog";
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
  private ListPluginComponent createListComponent(@NotNull IdeaPluginDescriptor updateDescriptor) {
    //noinspection unchecked
    ListPluginComponent component = new ListPluginComponent(myPluginModel, updateDescriptor, LinkListener.NULL, true);
    component.setOnlyUpdateMode();
    component.getChooseUpdateButton().addActionListener(e -> updateButtons());
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

    myGroup.ui.panel.getParent().remove(myGroup.ui.panel);
    myGroup.ui.panel.setPreferredSize(new Dimension());

    JPanel leftPanel = new JPanel(new BorderLayout());
    leftPanel.add(PluginManagerConfigurable.createScrollPane(myPluginsPanel, true));

    OpaquePanel titlePanel = new OpaquePanel(new BorderLayout(), PluginManagerConfigurable.MAIN_BG_COLOR);
    titlePanel.setBorder(JBUI.Borders.empty(13, 12));
    leftPanel.add(titlePanel, BorderLayout.SOUTH);

    myTotalLabel.setForeground(PluginsGroupComponent.SECTION_HEADER_FOREGROUND);
    titlePanel.add(myTotalLabel);

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

  static void ignorePlugins(@NotNull List<? extends IdeaPluginDescriptor> descriptors) {
    Set<String> ignoredPlugins = getIgnoredPlugins();

    for (IdeaPluginDescriptor descriptor : descriptors) {
      ignoredPlugins.add(getIdVersionValue(descriptor));
    }

    try {
      File file = getDisabledUpdateFile();
      FileUtil.writeToFile(file, StringUtil.join(ignoredPlugins, LineSeparator.getSystemLineSeparator().getSeparatorString()));
    }
    catch (IOException e) {
      Logger.getInstance(UpdateChecker.class).error(e);
    }

    SettingsEntryPointAction.removePluginsUpdate(descriptors);
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
}