// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.*;
import com.intellij.ide.plugins.enums.PluginsGroupType;
import com.intellij.ide.plugins.newui.*;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Divider;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author Alexander Lobas
 */
@ApiStatus.Internal
public final class PluginUpdateDialog extends DialogWrapper {
  private final @NotNull Collection<PluginDownloader> myDownloaders;
  private final boolean myPlatformUpdate;
  private final MyPluginModel myPluginModel;
  private final PluginsGroupComponent myPluginsPanel;
  private final PluginsGroup myGroup = new PluginsGroup("", PluginsGroupType.UPDATE);
  private final PluginDetailsPageComponent myDetailsPage;
  private final JLabel myTotalLabel = new JLabel();
  private final ActionLink myIgnoreAction;
  private final JBCheckBox myAutoUpdateOption;

  private @Nullable Runnable myFinishCallback;

  public PluginUpdateDialog(@Nullable Project project,
                     @NotNull Collection<PluginDownloader> downloaders,
                     @Nullable Collection<PluginUiModel> customRepositoryPlugins) {
    this(project, downloaders, customRepositoryPlugins, false);
    setTitle(IdeBundle.message("dialog.title.plugin.updates"));
  }

  public PluginUpdateDialog(@Nullable Project project, @NotNull Collection<PluginDownloader> updatesForPlugins) {
    this(project, updatesForPlugins, null, true);
    setTitle(IdeBundle.message("updates.dialog.title", ApplicationNamesInfo.getInstance().getFullProductName()));
  }

  private PluginUpdateDialog(@Nullable Project project,
                             Collection<PluginDownloader> downloaders,
                             @Nullable Collection<PluginUiModel> customRepositoryPlugins,
                             boolean platformUpdate) {
    super(project, true);

    myDownloaders = downloaders;
    myPlatformUpdate = platformUpdate;

    myIgnoreAction = new ActionLink(IdeBundle.message("updates.ignore.updates.button", downloaders.size()), e -> {
      close(CANCEL_EXIT_CODE);
      UpdateChecker.ignorePlugins(ContainerUtil.map(myGroup.ui.plugins, ListPluginComponent::getPluginDescriptor));
    });

    myAutoUpdateOption =
      new JBCheckBox(IdeBundle.message("updates.auto.update.title"), UpdateSettings.getInstance().getState().isPluginsAutoUpdateEnabled());

    myPluginModel = new MyPluginModel(project) {
      @Override
      public void runRestartButton(@NotNull Component component) {
        doOKAction();
      }

      @Override
      protected @NotNull Collection<PluginUiModel> getCustomRepoPlugins() {
        return customRepositoryPlugins != null ? customRepositoryPlugins : super.getCustomRepoPlugins();
      }
    };

    myPluginModel.setTopController(Configurable.TopComponentController.EMPTY);
    myPluginModel.setPluginUpdatesService(new PluginUpdatesService() {
      @Override
      public void finishUpdate() { }
    });

    //noinspection unchecked
    myDetailsPage = new PluginDetailsPageComponent(new PluginModelFacade(myPluginModel), LinkListener.NULL, true);
    myDetailsPage.setOnlyUpdateMode();

    MultiSelectionEventHandler eventHandler = new MultiSelectionEventHandler();

    myPluginsPanel = new PluginsGroupComponent(eventHandler) {
      @Override
      protected @NotNull ListPluginComponent createListComponent(@NotNull PluginUiModel model,
                                                                 @NotNull PluginsGroup group,
                                                                 @NotNull List<HtmlChunk> errors,
                                                                 @Nullable PluginUiModel installedDescriptorDorMarketplace) {
        if (!(model.isFromMarketplace())) {
          PluginNode node = new PluginNode(model.getPluginId(), model.getName(), "0");
          node.setDescription(model.getDescription());
          node.setChangeNotes(model.getChangeNotes());
          node.setVersion(model.getVersion());
          node.setVendor(model.getVendor());
          node.setVendorDetails(model.getOrganization());
          List<PluginDependencyImpl> dependencies =
            ContainerUtil.map(model.getDependencies(), it -> new PluginDependencyImpl(it.getPluginId(), null, it.isOptional()));
          node.setDependencies(dependencies);
          model = new PluginUiModelAdapter(node);
        }
        CoroutineScope scope = ApplicationManager.getApplication().getService(CoreUiCoroutineScopeHolder.class).coroutineScope;
        @SuppressWarnings("unchecked") ListPluginComponent component = new ListPluginComponent(new PluginModelFacade(myPluginModel), model, group, LinkListener.NULL, errors, scope, true);
        component.setOnlyUpdateMode();
        component.getChooseUpdateButton().addActionListener(e -> updateButtons());
        return component;
      }
    };
    PluginManagerConfigurable.registerCopyProvider(myPluginsPanel);
    myPluginsPanel.setSelectionListener(__ -> myDetailsPage.showPlugins(myPluginsPanel.getSelection()));

    for (PluginDownloader plugin : downloaders) {
      myGroup.addDescriptor(plugin.getDescriptor());
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

        IdeaPluginDescriptor descriptor = plugin.getPluginDescriptor();
        if (descriptor instanceof PluginNode) {
          total += ((PluginNode)descriptor).getIntegerSize();
        }
      }
    }

    myTotalLabel.setText(IdeBundle.message("plugin.update.dialog.total.label",
                                           StringUtilRt.formatFileSize(total).toUpperCase(Locale.ENGLISH)));
    myTotalLabel.setVisible(total > 0);
    getOKAction().setEnabled(count > 0);
  }

  public void setFinishCallback(@NotNull Runnable finishCallback) {
    myFinishCallback = finishCallback;
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();

    if (PluginManagementPolicy.getInstance().isPluginAutoUpdateAllowed()) {
      UpdateOptions state = UpdateSettings.getInstance().getState();
      boolean selected = myAutoUpdateOption.isSelected();
      if (state.isPluginsAutoUpdateEnabled() != selected) {
        state.setPluginsAutoUpdateEnabled(selected);
        ApplicationManager.getApplication().getService(PluginAutoUpdateService.class).onSettingsChanged$intellij_platform_ide_impl();
      }
    }

    if (myPlatformUpdate) return;

    List<PluginDownloader> toDownloads = new ArrayList<>();

    for (PluginDownloader downloader : myDownloaders) {
      ListPluginComponent component = Objects.requireNonNull(myGroup.ui.findComponent(downloader.getDescriptor()));
      if (component.getChooseUpdateButton().isSelected()) {
        toDownloads.add(downloader);
      }
    }

    runUpdateAll(toDownloads, getContentPanel(), myFinishCallback, null);
  }

  static void runUpdateAll(@NotNull Collection<PluginDownloader> toDownload,
                           @Nullable JComponent ownerComponent,
                           @Nullable Runnable finishCallback,
                           @Nullable Consumer<Boolean> customRestarter) {
    String message = IdeBundle.message("updates.notification.title", ApplicationNamesInfo.getInstance().getFullProductName());
    new Task.Backgroundable(null, message, true, PerformInBackgroundOption.DEAF) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        List<PluginDownloader> downloaders = downloadPluginUpdates(toDownload, indicator);
        if (downloaders.isEmpty()) {
          return;
        }

        ApplicationManager.getApplication().invokeLater(() -> {
          List<IdeaPluginDescriptor> installedDescriptors = installPluginUpdates(downloaders);

          boolean restartRequired = downloaders.size() != installedDescriptors.size();
          if (customRestarter != null) {
            customRestarter.accept(restartRequired);
            return;
          }
          if (PluginManagementPolicy.getInstance().isPluginAutoUpdateAllowed() &&
              !UpdateSettings.getInstance().getState().isPluginsAutoUpdateEnabled()) {
            Notification notification = UpdateChecker.getNotificationGroupForPluginUpdateResults()
              .createNotification(IdeBundle.message("updates.plugins.notification.title"),
                                  IdeBundle.message("updates.plugins.autoupdate.notification.message"), NotificationType.INFORMATION)
              .addAction(new NotificationAction(IdeBundle.message("updates.auto.update.title")) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                  UpdateSettings.getInstance().getState().setPluginsAutoUpdateEnabled(true);
                  ApplicationManager.getApplication().getService(PluginAutoUpdateService.class)
                    .onSettingsChanged$intellij_platform_ide_impl();
                  notification.expire();
                }
              })
              .addAction(new NotificationAction(IdeBundle.message("label.dont.show")) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                  notification.setDoNotAskFor(null);
                  notification.expire();
                }
              });
            notification.configureDoNotAskOption("updates.plugins.autoupdate.notification",
                                                 IdeBundle.message("updates.plugins.autoupdate.notification.do.not.ask.display"));
            notification.notify(myProject);
          }
          if (!restartRequired) {
            UpdateChecker.getNotificationGroupForPluginUpdateResults()
              .createNotification(getUpdateNotificationMessage(installedDescriptors),
                                  NotificationType.INFORMATION)
              .setDisplayId("plugins.updated.without.restart")
              .notify(myProject);
          }
          else if (WelcomeFrame.getInstance() == null) {
            PluginManagerMain.notifyPluginsUpdated(null);
          }
          else {
            PluginManagerConfigurable.shutdownOrRestartApp();
          }
        }, ownerComponent != null ? ModalityState.stateForComponent(ownerComponent) : ModalityState.defaultModalityState());
      }

      @Override
      public void onFinished() {
        if (finishCallback != null) {
          finishCallback.run();
        }
      }

      private @NotNull List<IdeaPluginDescriptor> installPluginUpdates(@NotNull List<PluginDownloader> downloaders) {
        List<IdeaPluginDescriptor> installedDescriptors = new ArrayList<>();
        for (PluginDownloader downloader : downloaders) {
          try {
            if (downloader.installDynamically(ownerComponent)) {
              installedDescriptors.add(downloader.getDescriptor());
            }
          }
          catch (Exception e) {
            Logger.getInstance(PluginUpdateDialog.class).info(e);
          }
        }
        return installedDescriptors;
      }

      private static @NotNull @Nls String getUpdateNotificationMessage(@NotNull List<? extends IdeaPluginDescriptor> descriptors) {
        if (descriptors.size() == 1) {
          IdeaPluginDescriptor descriptor = descriptors.get(0);
          return IdeBundle.message("notification.content.updated.plugin.to.version", descriptor.getName(), descriptor.getVersion());
        }
        else {
          String names = descriptors.stream()
            .map(IdeaPluginDescriptor::getName)
            .collect(Collectors.joining(", "));
          return IdeBundle.message("notification.content.updated.plugins", names);
        }
      }
    }.queue();
  }

  private static @NotNull List<PluginDownloader> downloadPluginUpdates(@NotNull Collection<PluginDownloader> toDownload,
                                                                       @NotNull ProgressIndicator indicator) {
    LinkedHashSet<@Nls String> errors = new LinkedHashSet<>();
    try {
      List<PluginDownloader> downloaders = ContainerUtil.map(toDownload,
                                                             downloader -> downloader.withErrorsConsumer(errors::add));
      return UpdateInstaller.downloadPluginUpdates(downloaders, indicator);
    }
    finally {
      if (!errors.isEmpty()) {
        String text = String.join("\n\n", errors); //NON-NLS
        PluginDownloader.showErrorDialog(text);
      }
    }
  }

  @Override
  public void doCancelAction() {
    close(CANCEL_EXIT_CODE);
  }

  @Override
  protected JPanel createSouthAdditionalPanel() {
    JPanel panel = new Wrapper(myIgnoreAction);
    panel.setBorder(JBUI.Borders.emptyLeft(10));
    return panel;
  }

  @Override
  protected @NotNull JPanel createButtonsPanel(@NotNull List<? extends JButton> buttons) {
    JPanel panel = super.createButtonsPanel(buttons);
    if (PluginManagementPolicy.getInstance().isPluginAutoUpdateAllowed()) {
      JPanel buttonsPanel = new NonOpaquePanel(new BorderLayout(JBUI.scale(10), 0));
      buttonsPanel.add(myAutoUpdateOption, BorderLayout.WEST);
      buttonsPanel.add(panel);
      return buttonsPanel;
    }
    return panel;
  }

  @Override
  protected String getHelpId() {
    return "plugin.update.dialog";
  }

  @Override
  protected @NotNull DialogStyle getStyle() {
    return DialogStyle.COMPACT;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.openapi.updateSettings.impl.PluginUpdateInfoDialog";
  }

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
}
