// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.config.AntBuildFileBase;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Service(Service.Level.PROJECT)
@State(name = "antWorkspaceConfiguration", storages = {
  @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE),
  @Storage(value = StoragePathMacros.WORKSPACE_FILE, deprecated = true)
})
public final class AntWorkspaceConfiguration implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(AntWorkspaceConfiguration.class);

  private final Project myProject;
  @NonNls private static final String BUILD_FILE = "buildFile";
  @NonNls private static final String URL = "url";
  private final AtomicReference<Element> myProperties = new AtomicReference<>(null);

  public boolean IS_AUTOSCROLL_TO_SOURCE;
  public boolean FILTER_TARGETS;

  public AntWorkspaceConfiguration(Project project) {
    myProject = project;
  }

  @Override
  public Element getState() {
    final Element e = new Element("state");
    writeExternal(e);
    return e;
  }

  @Override
  public void loadState(@NotNull Element state) {
    try {
      readExternal(state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, parentNode);
    myProperties.set(parentNode);
  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, parentNode);
    for (final AntBuildFileBase buildFile : AntConfiguration.getInstance(myProject).getBuildFileList()) {
      Element element = new Element(BUILD_FILE);
      element.setAttribute(URL, buildFile.getVirtualFile().getUrl());
      buildFile.writeWorkspaceProperties(element);
      parentNode.addContent(element);
    }
  }

  public static AntWorkspaceConfiguration getInstance(Project project) {
    return project.getService(AntWorkspaceConfiguration.class);
  }

  public void loadFileProperties() throws InvalidDataException {
    final Element properties = myProperties.getAndSet(null);
    if (properties == null) {
      return;
    }
    for (final AntBuildFileBase buildFile : AntConfiguration.getInstance(myProject).getBuildFileList()) {
      VirtualFile file = buildFile.getVirtualFile();
      Element fileElement = file != null? findChildByUrl(properties, file.getUrl()) : null;
      if (fileElement != null) {
        buildFile.readWorkspaceProperties(fileElement);
      }
    }
  }

  @Nullable
  private static Element findChildByUrl(Element parentNode, String url) {
    for (Element element : parentNode.getChildren(BUILD_FILE)) {
      if (Objects.equals(element.getAttributeValue(URL), url)) {
        return element;
      }
    }
    return null;
  }
}
