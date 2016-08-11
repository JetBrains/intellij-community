/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildFileBase;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

@State(name = "antWorkspaceConfiguration", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class AntWorkspaceConfiguration implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.config.impl.AntWorkspaceConfiguration");

  private final Project myProject;
  @NonNls private static final String BUILD_FILE = "buildFile";
  @NonNls private static final String URL = "url";
  private final AtomicReference<Element> myProperties = new AtomicReference<>(null);

  public boolean IS_AUTOSCROLL_TO_SOURCE;
  public boolean FILTER_TARGETS;

  public AntWorkspaceConfiguration(Project project) {
    myProject = project;
  }

  public Element getState() {
    try {
      final Element e = new Element("state");
      writeExternal(e);
      return e;
    }
    catch (WriteExternalException e1) {
      LOG.error(e1);
      return null;
    }
  }

  public void loadState(Element state) {
    try {
      readExternal(state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    loadGlobalSettings(parentNode);
    myProperties.set(parentNode);
  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, parentNode);
    for (final AntBuildFile buildFile : AntConfiguration.getInstance(myProject).getBuildFileList()) {
      Element element = new Element(BUILD_FILE);
      element.setAttribute(URL, buildFile.getVirtualFile().getUrl());
      ((AntBuildFileBase)buildFile).writeWorkspaceProperties(element);
      parentNode.addContent(element);
    }
  }

  public static AntWorkspaceConfiguration getInstance(Project project) {
    return ServiceManager.getService(project, AntWorkspaceConfiguration.class);
  }

  public void loadFileProperties() throws InvalidDataException {
    final Element properties = myProperties.getAndSet(null);
    if (properties == null) {
      return;
    }
    for (final AntBuildFile buildFile : AntConfiguration.getInstance(myProject).getBuildFileList()) {
      final Element fileElement = findChildByUrl(properties, buildFile.getVirtualFile().getUrl());
      if (fileElement == null) {
        continue;
      }
      ((AntBuildFileBase)buildFile).readWorkspaceProperties(fileElement);
    }
  }

  public void loadFromProjectSettings(Element parentNode) throws InvalidDataException {
    loadGlobalSettings(parentNode);
  }

  private void loadGlobalSettings(Element parentNode) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, parentNode);
  }

  @Nullable
  private static Element findChildByUrl(Element parentNode, String url) {
    for (Element element : parentNode.getChildren(BUILD_FILE)) {
      if (Comparing.equal(element.getAttributeValue(URL), url)) {
        return element;
      }
    }
    return null;
  }
}
