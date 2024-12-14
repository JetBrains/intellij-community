// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.PluginNode;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.updateSettings.impl.DetectedPluginsPanel;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.util.ui.JBDimension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class PluginsAdvertiserDialog extends DialogWrapper {
  private final Collection<PluginDownloader> myPluginToInstall;
  private final @Nullable Project myProject;
  private final @Nullable Consumer<Boolean> myFinishFunction;
  private final boolean mySelectAllSuggestions;
  private @Nullable DetectedPluginsPanel myPanel;

  PluginsAdvertiserDialog(
    @Nullable Project project,
    @NotNull Collection<PluginDownloader> pluginsToInstall,
    boolean selectAllSuggestions,
    @Nullable Consumer<Boolean> finishFunction
  ) {
    super(project);
    myProject = project;
    myPluginToInstall = pluginsToInstall;
    myFinishFunction = finishFunction;
    mySelectAllSuggestions = selectAllSuggestions;
    setTitle(IdeBundle.message("dialog.title.choose.plugins.to.install.or.enable"));
    init();

    var rootPane = getPeer().getRootPane();
    if (rootPane != null) {
      rootPane.setPreferredSize(new JBDimension(800, 600));
    }
  }

  public PluginsAdvertiserDialog(@Nullable Project project, @NotNull Collection<PluginDownloader> pluginsToInstall) {
    this(project, pluginsToInstall, false, null);
  }

  /**
   * @deprecated custom repositories are no longer supported by the plugin advertiser;
   * use {@link #PluginsAdvertiserDialog(Project, Collection<PluginDownloader>)} instead.
   */
  @Deprecated(forRemoval = true)
  public PluginsAdvertiserDialog(@Nullable Project project, @NotNull Collection<PluginDownloader> pluginsToInstall, @NotNull List<PluginNode> ignored) {
    this(project, pluginsToInstall, false, null);
  }

  @Override
  protected @NotNull JComponent createCenterPanel() {
    if (myPanel == null) {
      myPanel = new DetectedPluginsPanel(myProject);
      // all or nothing, a single plugin always gets selected automatically
      var checkAll = mySelectAllSuggestions || myPluginToInstall.size() == 1;
      for (var downloader : myPluginToInstall) {
        myPanel.setChecked(downloader, checkAll);
      }
      myPanel.addAll(myPluginToInstall);
    }
    return myPanel;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
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
   * @param showDialog    whether the dialog will be shown to a user
   * @param modalityState modality state used by the plugin installation process.
   *                      {@code modalityState} will taken into account only if {@code showDialog} is <code>false</code>.
   *                      If {@code null} is passed, {@code ModalityState.NON_MODAL} will be used
   */
  public void doInstallPlugins(boolean showDialog, @Nullable ModalityState modalityState) {
    if (showDialog) {
      showAndGet();
    }
    else {
      doInstallPlugins(__ -> true, modalityState != null ? modalityState : ModalityState.nonModal());
    }
  }

  private boolean doInstallPlugins(@NotNull Predicate<? super PluginDownloader> predicate, @NotNull ModalityState modalityState) {
    return new PluginsAdvertiserDialogPluginInstaller(myProject, myPluginToInstall, List.of(), myFinishFunction)
      .doInstallPlugins(predicate, modalityState);
  }
}
