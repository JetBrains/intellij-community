/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
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
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.VcsEP");

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

  private AbstractVcs myVcs;
  private final Object LOCK = new Object();

  @Nullable
  public AbstractVcs getVcs(@NotNull Project project) {
    synchronized (LOCK) {
      if (myVcs != null) {
        return myVcs;
      }
    }
    AbstractVcs vcs = getInstance(project, vcsClass);
    synchronized (LOCK) {
      if (myVcs == null && vcs != null) {
        vcs.setupEnvironments();
        myVcs = vcs;
      }
      return myVcs;
    }
  }

  @Nullable
  private AbstractVcs getInstance(@NotNull Project project, @NotNull String vcsClass) {
    try {
      final Class<? extends AbstractVcs> foundClass = findClass(vcsClass);
      final Class<?>[] interfaces = foundClass.getInterfaces();
      for (Class<?> anInterface : interfaces) {
        if (BaseComponent.class.isAssignableFrom(anInterface)) {
          return project.getComponent(foundClass);
        }
      }
      return instantiate(vcsClass, project.getPicoContainer());
    }
    catch (ProcessCanceledException pce) {
      throw pce;
    }
    catch(Exception e) {
      LOG.error(e);
      return null;
    }
  }

  @NotNull
  public VcsDescriptor createDescriptor() {
    return new VcsDescriptor(administrativeAreaName, displayName, name, crawlUpToCheckUnderVcs);
  }
}
