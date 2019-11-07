// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerMain;
import com.intellij.ide.plugins.RepositoryHelper;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.impl.VcsDescriptor;
import com.intellij.openapi.vcs.impl.VcsEP;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.openapi.vcs.VcsNotifier.IMPORTANT_ERROR_NOTIFICATION;

public final class AllVcses implements AllVcsesI, Disposable {
  private final Logger LOG = Logger.getInstance(AllVcses.class);
  private final Map<String, AbstractVcs> myVcses;

  private final Object myLock;
  private final Project myProject;
  private final Map<String, VcsEP> myExtensions;    // +-

  private final AtomicBoolean unbundledVcsNotificationShown = new AtomicBoolean();

  private AllVcses(final Project project) {
    myProject = project;
    myVcses = new HashMap<>();
    myLock = new Object();

    final VcsEP[] vcsEPs = VcsEP.EP_NAME.getExtensions(myProject);
    final HashMap<String, VcsEP> map = new HashMap<>();
    for (VcsEP vcsEP : vcsEPs) {
      map.put(vcsEP.name, vcsEP);
    }
    myExtensions = Collections.unmodifiableMap(map);
  }

  public static AllVcsesI getInstance(final Project project) {
    return ServiceManager.getService(project, AllVcsesI.class);
  }

  private void addVcs(final AbstractVcs vcs) {
    registerVcs(vcs);
    myVcses.put(vcs.getName(), vcs);
  }

  private void registerVcs(final AbstractVcs vcs) {
    try {
      vcs.loadSettings();
      vcs.doStart();
    }
    catch (VcsException e) {
      LOG.warn(e);
    }
    vcs.getProvidedStatuses();
  }

  @Override
  public void registerManually(@NotNull final AbstractVcs vcs) {
    synchronized (myLock) {
      if (myVcses.containsKey(vcs.getName())) return;
      addVcs(vcs);
    }
  }

  @Override
  public void unregisterManually(@NotNull final AbstractVcs vcs) {
    synchronized (myLock) {
      if (! myVcses.containsKey(vcs.getName())) return;
      unregisterVcs(vcs);
      myVcses.remove(vcs.getName());
    }
  }

  @Override
  public AbstractVcs getByName(final String name) {
    if (StringUtil.isEmpty(name)) return null;

    synchronized (myLock) {
      final AbstractVcs vcs = myVcses.get(name);
      if (vcs != null) {
        return vcs;
      }
    }

    // unmodifiable map => no sync needed
    final VcsEP ep = myExtensions.get(name);
    if (ep == null) {
      ObsoleteVcs obsoleteVcs = ObsoleteVcs.findByName(name);
      if (obsoleteVcs != null && unbundledVcsNotificationShown.compareAndSet(false, true)) {
        proposeToInstallPlugin(obsoleteVcs);
      }
      return null;
    }

    // VcsEP guarantees to always return the same vcs value
    final AbstractVcs vcs1 = ep.getVcs(myProject);
    LOG.assertTrue(vcs1 != null, name);

    synchronized (myLock) {
      if (!myVcses.containsKey(name)) {
        addVcs(vcs1);
      }
      return vcs1;
    }
  }

  @Nullable
  @Override
  public VcsDescriptor getDescriptor(String name) {
    final VcsEP ep = myExtensions.get(name);
    return ep == null ? null : ep.createDescriptor();
  }

  @Override
  public void dispose() {
    synchronized (myLock) {
      for (AbstractVcs vcs : myVcses.values()) {
        unregisterVcs(vcs);
      }
    }
  }

  private void unregisterVcs(AbstractVcs vcs) {
    try {
      vcs.doShutdown();
    }
    catch (VcsException e) {
      LOG.warn(e);
    }
  }

  @Override
  public boolean isEmpty() {
    return myExtensions.isEmpty();
  }

  @Override
  public VcsDescriptor[] getAll() {
    final List<VcsDescriptor> result = new ArrayList<>(myExtensions.size());
    for (VcsEP vcsEP : myExtensions.values()) {
      result.add(vcsEP.createDescriptor());
    }
    Collections.sort(result);
    return result.toArray(new VcsDescriptor[0]);
  }

  private enum ObsoleteVcs {
    CVS("CVS", "CVS", "https://plugins.jetbrains.com/plugin/10746-cvs-integration"),
    TFS("TFS", "TFS", "https://plugins.jetbrains.com/plugin/4578-tfs");

    @NotNull private final String vcsName;
    @NotNull private final PluginId pluginId;
    @NotNull private final String pluginUrl;

    ObsoleteVcs(@NotNull String vcsName, @NotNull String pluginId, @NotNull String pluginUrl) {
      this.vcsName = vcsName;
      this.pluginId = PluginId.getId(pluginId);
      this.pluginUrl = pluginUrl;
    }

    @Nullable
    public static ObsoleteVcs findByName(@NotNull String name) {
      return ContainerUtil.find(values(), vcs -> vcs.vcsName.equals(name));
    }
  }

  private void proposeToInstallPlugin(@NotNull ObsoleteVcs vcs) {
    String message = "The " + vcs + " plugin was unbundled and needs to be installed manually";
    Notification notification = IMPORTANT_ERROR_NOTIFICATION.createNotification("", message, NotificationType.WARNING, null);
    notification.addAction(NotificationAction.createSimple("Install", () -> {
      notification.expire();
      installPlugin(vcs);
    }));
    notification.addAction(NotificationAction.createSimple("Read More", () -> {
      BrowserUtil.browse("https://blog.jetbrains.com/idea/2019/02/unbundling-tfs-and-cvs-integration-plugins/");
    }));
    VcsNotifier.getInstance(myProject).notify(notification);
  }

  private void installPlugin(@NotNull ObsoleteVcs vcs) {
    new Task.Backgroundable(myProject, "Installing Plugin") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          List<IdeaPluginDescriptor> plugins = RepositoryHelper.loadPlugins(indicator);
          IdeaPluginDescriptor descriptor = ContainerUtil.find(plugins, d -> d.getPluginId() == vcs.pluginId);
          if (descriptor != null) {
            PluginDownloader downloader = PluginDownloader.createDownloader(descriptor);
            if (downloader.prepareToInstall(indicator)) {
              downloader.install();
              PluginManager.getInstance().enablePlugins(Collections.singletonList(descriptor), true);
              PluginManagerMain.notifyPluginsUpdated(myProject);
            }
          }
          else {
            showErrorNotification(vcs, "Couldn't find the plugin " + vcs.pluginId);
          }
        }
        catch (IOException e) {
          LOG.warn(e);
          showErrorNotification(vcs, e.getMessage());
        }
      }

      private void showErrorNotification(@NotNull ObsoleteVcs vcs, @NotNull String message) {
        String title = "Failed to Install Plugin";
        Notification notification = IMPORTANT_ERROR_NOTIFICATION.createNotification(title, message, NotificationType.ERROR, null);
        notification.addAction(NotificationAction.createSimple("Open Plugin Page", () -> {
          BrowserUtil.browse(vcs.pluginUrl);
        }));
        VcsNotifier.getInstance(myProject).notify(notification);
      }
    }.queue();
  }
}
