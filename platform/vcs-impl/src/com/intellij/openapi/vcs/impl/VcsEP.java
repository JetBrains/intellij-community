// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionInstantiationException;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class VcsEP extends AbstractExtensionPointBean {
  private static final Logger LOG = Logger.getInstance(VcsEP.class);

  public static final ExtensionPointName<VcsEP> EP_NAME = ExtensionPointName.create("com.intellij.vcs");

  // these must be public for scrambling compatibility
  @Attribute("name")
  public String name;
  @Attribute("vcsClass")
  public String vcsClass;
  @Attribute("displayName")
  public String displayName;
  @Attribute("administrativeAreaName")
  public String administrativeAreaName;
  @Attribute("crawlUpToCheckUnderVcs")
  public boolean crawlUpToCheckUnderVcs;
  @Attribute("areChildrenValidMappings")
  public boolean areChildrenValidMappings;

  @Nullable
  public AbstractVcs createVcs(@NotNull Project project) {
    try {
      @NotNull Class<AbstractVcs> result;
      try {
        result = findClass(vcsClass, myPluginDescriptor);
      }
      catch (Throwable t) {
        throw new ExtensionInstantiationException(t, myPluginDescriptor);
      }
      Class<? extends AbstractVcs> foundClass = result;
      Class<?>[] interfaces = foundClass.getInterfaces();
      for (Class<?> anInterface : interfaces) {
        if (BaseComponent.class.isAssignableFrom(anInterface)) {
          return project.getComponent(foundClass);
        }
      }
      return instantiateClass(vcsClass, project.getPicoContainer());
    }
    catch (ProcessCanceledException pce) {
      throw pce;
    }
    catch (Exception e) {
      LOG.error(new PluginException(e, getPluginId()));
      return null;
    }
  }

  @NotNull
  public VcsDescriptor createDescriptor() {
    return new VcsDescriptor(administrativeAreaName, displayName, name, crawlUpToCheckUnderVcs, areChildrenValidMappings);
  }
}
