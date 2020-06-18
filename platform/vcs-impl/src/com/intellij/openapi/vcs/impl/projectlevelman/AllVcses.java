// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.plugins.DisabledPluginsState;
import com.intellij.ide.plugins.PluginManagerMain;
import com.intellij.ide.plugins.PluginNode;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
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

  private final Project myProject;

  private final Object myLock = new Object();
  private final Map<String, AbstractVcs> myVcses = new HashMap<>();
  private final Map<String, VcsEP> myExtensions = new HashMap<>();

  private final AtomicBoolean unbundledVcsNotificationShown = new AtomicBoolean();

  private AllVcses(@NotNull Project project) {
    myProject = project;

    // Do not fire 'scheduleMappingsUpdate' to avoid cyclic service dependency
    for (VcsEP extension : VcsEP.EP_NAME.getExtensionList()) {
      String name = extension.name;
      VcsEP oldEp = myExtensions.put(name, extension);
      if (oldEp != null) {
        LOG.error(String.format("registering duplicated EP. name: %s, old: %s, new: %s", name, oldEp.vcsClass, extension.vcsClass));
      }
    }

    VcsEP.EP_NAME.addExtensionPointListener(new MyExtensionPointListener(), project);
  }

  public static AllVcsesI getInstance(@NotNull Project project) {
    return project.getService(AllVcsesI.class);
  }

  @Override
  public void registerManually(@NotNull AbstractVcs vcs) {
    ReadAction.run(() -> {
      synchronized (myLock) {
        String name = vcs.getName();
        if (myVcses.containsKey(name)) {
          LOG.error(String.format("vcs is already registered: %s", vcs), new Throwable());
          return;
        }
        if (myExtensions.containsKey(name)) {
          LOG.error(String.format("can't override vcs from EP. vcs: %s, ep: %s", vcs, myExtensions.get(name).vcsClass), new Throwable());
          return;
        }
        myVcses.put(name, vcs);
        registerVcs(vcs);
      }
    });
    ProjectLevelVcsManagerImpl.getInstanceImpl(myProject).updateMappedVcsesImmediately();
  }

  @Override
  public void unregisterManually(@NotNull AbstractVcs vcs) {
    ReadAction.run(() -> {
      synchronized (myLock) {
        String name = vcs.getName();
        if (!myVcses.containsKey(name)) {
          LOG.error(String.format("vcs is not registered: %s", vcs), new Throwable());
          return;
        }
        myVcses.remove(name);
        unregisterVcs(vcs);
      }
    });
    ProjectLevelVcsManagerImpl.getInstanceImpl(myProject).updateMappedVcsesImmediately();
  }

  @Override
  public AbstractVcs getByName(@Nullable String name) {
    if (StringUtil.isEmpty(name)) {
      return null;
    }

    VcsEP ep;
    synchronized (myLock) {
      AbstractVcs vcs = myVcses.get(name);
      if (vcs != null) {
        return vcs;
      }

      ep = myExtensions.get(name);
    }

    if (ep == null) {
      ObsoleteVcs obsoleteVcs = ObsoleteVcs.findByName(name);
      if (obsoleteVcs != null && unbundledVcsNotificationShown.compareAndSet(false, true)) {
        proposeToInstallPlugin(obsoleteVcs);
      }
      return null;
    }

    AbstractVcs vcs = ep.createVcs(myProject);
    LOG.assertTrue(name.equals(vcs.getName()), vcs);

    vcs.setupEnvironments();

    return ReadAction.compute(() -> {
      synchronized (myLock) {
        if (myExtensions.get(name) != ep) return null;

        AbstractVcs oldVcs = myVcses.get(name);
        if (oldVcs != null) {
          return oldVcs;
        }

        myVcses.put(name, vcs);
        registerVcs(vcs);
        return vcs;
      }
    });
  }

  @Nullable
  @Override
  public VcsDescriptor getDescriptor(String name) {
    synchronized (myLock) {
      final VcsEP ep = myExtensions.get(name);
      return ep == null ? null : ep.createDescriptor();
    }
  }

  @Override
  public void dispose() {
    synchronized (myLock) {
      for (AbstractVcs vcs : myVcses.values()) {
        unregisterVcs(vcs);
      }
      myVcses.clear();
    }
  }

  private void registerVcs(@NotNull AbstractVcs vcs) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    try {
      vcs.loadSettings();
      vcs.doStart();
      vcs.getProvidedStatuses();
    }
    catch (VcsException e) {
      LOG.warn(e);
    }
  }

  private void unregisterVcs(@NotNull AbstractVcs vcs) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    try {
      vcs.doShutdown();
    }
    catch (VcsException e) {
      LOG.warn(e);
    }
  }

  @Override
  public boolean isEmpty() {
    synchronized (myLock) {
      return myExtensions.isEmpty();
    }
  }

  @Override
  public VcsDescriptor[] getAll() {
    final List<VcsDescriptor> result = new ArrayList<>();
    synchronized (myLock) {
      for (VcsEP vcsEP : myExtensions.values()) {
        result.add(vcsEP.createDescriptor());
      }
    }
    Collections.sort(result);
    return result.toArray(new VcsDescriptor[0]);
  }

  private class MyExtensionPointListener implements ExtensionPointListener<VcsEP> {
    @Override
    public void extensionAdded(@NotNull VcsEP extension, @NotNull PluginDescriptor pluginDescriptor) {
      synchronized (myLock) {
        String name = extension.name;
        VcsEP oldEp = myExtensions.put(name, extension);
        if (oldEp != null) {
          LOG.error(String.format("registering duplicated EP. name: %s, old: %s, new: %s", name, oldEp.vcsClass, extension.vcsClass));
        }

        AbstractVcs oldVcs = myVcses.remove(name);
        if (oldVcs != null) {
          LOG.error(String.format("overriding VCS with EP. name: %s, old: %s, new: %s", name, oldVcs.getClass(), extension.vcsClass));
          unregisterVcs(oldVcs);
        }
      }
      ProjectLevelVcsManagerImpl.getInstanceImpl(myProject).updateMappedVcsesImmediately();
    }

    @Override
    public void extensionRemoved(@NotNull VcsEP extension, @NotNull PluginDescriptor pluginDescriptor) {
      synchronized (myLock) {
        String name = extension.name;
        AbstractVcs oldVcs = myVcses.get(name);
        if (oldVcs != null) {
          myVcses.remove(name);
          unregisterVcs(oldVcs);
        }

        boolean wasRemoved = myExtensions.remove(name, extension);
        if (!wasRemoved) {
          LOG.error(String.format("removing unregistered EP. name: %s, ep: %s", name, extension.vcsClass));
        }
      }
      ProjectLevelVcsManagerImpl.getInstanceImpl(myProject).updateMappedVcsesImmediately();
    }
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
    notification
      .addAction(NotificationAction.createSimple(VcsBundle.messagePointer("action.NotificationAction.AllVcses.text.install"), () -> {
      notification.expire();
      installPlugin(vcs);
    }));
    notification.addAction(NotificationAction.createSimple(VcsBundle.messagePointer("action.NotificationAction.AllVcses.text.read.more"), () -> {
      BrowserUtil.browse("https://blog.jetbrains.com/idea/2019/02/unbundling-tfs-and-cvs-integration-plugins/");
    }));
    VcsNotifier.getInstance(myProject).notify(notification);
  }

  private void installPlugin(@NotNull ObsoleteVcs vcs) {
    new Task.Backgroundable(myProject, "Installing Plugin") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          PluginNode descriptor = MarketplaceRequests.getInstance().getLastCompatiblePluginUpdate(vcs.pluginId.getIdString(), null, indicator);
          if (descriptor != null) {
            PluginDownloader downloader = PluginDownloader.createDownloader(descriptor);
            if (downloader.prepareToInstall(indicator)) {
              downloader.install();
              DisabledPluginsState.enablePlugins(Collections.singletonList(descriptor), true);
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
        notification.addAction(
          NotificationAction.createSimple(VcsBundle.messagePointer("action.NotificationAction.AllVcses.text.open.plugin.page"), () -> {
          BrowserUtil.browse(vcs.pluginUrl);
        }));
        VcsNotifier.getInstance(myProject).notify(notification);
      }
    }.queue();
  }
}
