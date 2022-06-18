// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.*;
import com.intellij.ide.plugins.org.PluginManagerFilters;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.updateSettings.impl.DetectedPluginsPanel;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author anna
 */
public final class PluginsAdvertiserDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(PluginsAdvertiserDialog.class);

  private final Collection<PluginDownloader> myPluginToInstall;
  private final @Nullable Project myProject;
  private final @NotNull List<PluginNode> myCustomPlugins;
  private final @Nullable Consumer<? super Boolean> myFinishFunction;
  private final boolean mySelectAllSuggestions;
  private @Nullable DetectedPluginsPanel myPanel;

  PluginsAdvertiserDialog(@Nullable Project project,
                          @NotNull Collection<PluginDownloader> pluginsToInstall,
                          @NotNull List<PluginNode> customPlugins,
                          boolean selectAllSuggestions,
                          @Nullable Consumer<? super Boolean> finishFunction) {
    super(project);
    myProject = project;
    myPluginToInstall = pluginsToInstall;
    myCustomPlugins = customPlugins;
    myFinishFunction = finishFunction;
    mySelectAllSuggestions = selectAllSuggestions;
    setTitle(IdeBundle.message("dialog.title.choose.plugins.to.install.or.enable"));
    init();
  }

  PluginsAdvertiserDialog(@Nullable Project project,
                          @NotNull Collection<PluginDownloader> pluginsToInstall,
                          @NotNull List<PluginNode> customPlugins) {
    this(project, pluginsToInstall, customPlugins, false, null);
  }

  @Override
  protected @NotNull JComponent createCenterPanel() {
    if (myPanel == null) {
      myPanel = new DetectedPluginsPanel(myProject);
      Iterator<PluginDownloader> iterator = myPluginToInstall.iterator();
      myPanel.addAll(myPluginToInstall, mySelectAllSuggestions || !iterator.hasNext() ? null : iterator.next());
    }
    return myPanel;
  }

  @Override
  protected void doOKAction() {
    assert myPanel != null;
    if (doInstallPlugins(myPanel::isChecked, ModalityState.stateForComponent(myPanel))) {
      super.doOKAction();
    }
  }

  /**
   * @param showDialog if the dialog will be shown to a user or not
   * @param modalityState modality state used by plugin installation process.
   *                      {@code modalityState} will taken into account only if {@code showDialog} is <code>false</code>.
   *                      If {@code null} is passed, {@code ModalityState.NON_MODAL} will be used
   */
  public void doInstallPlugins(boolean showDialog, @Nullable ModalityState modalityState) {
    if (showDialog) {
      showAndGet();
    }
    else {
      doInstallPlugins(__ -> true, modalityState != null ? modalityState : ModalityState.NON_MODAL);
    }
  }

  private boolean doInstallPlugins(@NotNull Predicate<? super PluginDownloader> predicate, @NotNull ModalityState modalityState) {
    ArrayList<IdeaPluginDescriptor> pluginsToEnable = new ArrayList<>();
    ArrayList<PluginNode> nodes = new ArrayList<>();
    for (PluginDownloader downloader : myPluginToInstall) {
      if (predicate.test(downloader)) {
        IdeaPluginDescriptor plugin = downloader.getDescriptor();
        pluginsToEnable.add(plugin);
        if (plugin.isEnabled()) {
          nodes.add(downloader.toPluginNode());
        }
      }
    }

    if (!PluginManagerMain.checkThirdPartyPluginsAllowed(nodes)) {
      return false;
    }

    var org = PluginManagerFilters.getInstance();
    var notAllowedToInstallPlugins = nodes
      .stream()
      .filter(descriptor -> !org.allowInstallingPlugin(descriptor))
      .map(e -> e.getPluginId().getIdString())
      .collect(Collectors.toCollection(TreeSet<String>::new));

    if (!notAllowedToInstallPlugins.isEmpty()) {
      LOG.warn("Some advertised plugins were not allowed to install for " +
               "the organization: " + StringUtil.join(notAllowedToInstallPlugins, ", "));
      return false;
    }

    PluginManagerMain.suggestToEnableInstalledDependantPlugins(PluginEnabler.HEADLESS, nodes);

    Runnable notifyRunnable = () -> {
      boolean notInstalled = nodes.stream()
        .map(PluginNode::getPluginId)
        .map(PluginManagerCore::getPlugin)
        .anyMatch(Objects::isNull);
      if (notInstalled) {
        PluginManagerMain.notifyPluginsUpdated(myProject);
      }
    };

    PluginEnabler.HEADLESS.enable(pluginsToEnable);
    if (!nodes.isEmpty()) {
      try {
        PluginManagerMain.downloadPlugins(nodes,
                                          myCustomPlugins,
                                          true,
                                          notifyRunnable,
                                          PluginEnabler.HEADLESS,
                                          modalityState,
                                          myFinishFunction);
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
    return true;
  }
}
