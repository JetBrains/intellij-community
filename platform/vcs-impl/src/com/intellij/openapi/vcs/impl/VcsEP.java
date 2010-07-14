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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.util.xmlb.annotations.Attribute;

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

  private AbstractVcs myVcs;

  public AbstractVcs getVcs(Project project) {
    if (myVcs == null) {
      try {
        final Class<? extends AbstractVcs> foundClass = findClass(vcsClass);
        final Class<?>[] interfaces = foundClass.getInterfaces();
        for (Class<?> anInterface : interfaces) {
          if (BaseComponent.class.isAssignableFrom(anInterface)) {
            myVcs = project.getComponent(foundClass);
            return myVcs;
          }
        }
        myVcs = instantiate(vcsClass, project.getPicoContainer());
      }
      catch(Exception e) {
        LOG.error(e);
        return null;
      }
    }
    return myVcs;
  }

  public VcsDescriptor createDescriptor() {
    return new VcsDescriptor(administrativeAreaName, displayName, name);
  }
}