// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.impl.VcsDescriptor;
import com.intellij.openapi.vcs.impl.VcsEP;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class AllVcses implements AllVcsesI, Disposable {
  private final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.projectlevelman.AllVcses");
  private final Map<String, AbstractVcs> myVcses;

  private final Object myLock;
  private final Project myProject;
  private final Map<String, VcsEP> myExtensions;    // +-

  private AllVcses(final Project project) {
    myProject = project;
    myVcses = new HashMap<>();
    myLock = new Object();

    final VcsEP[] vcsEPs = Extensions.getExtensions(VcsEP.EP_NAME, myProject);
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
      LOG.debug(e);
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
    synchronized (myLock) {
      final AbstractVcs vcs = myVcses.get(name);
      if (vcs != null) {
        return vcs;
      }
    }

    // unmodifiable map => no sync needed
    final VcsEP ep = myExtensions.get(name);
    if (ep == null) {
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
      LOG.info(e);
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
}
