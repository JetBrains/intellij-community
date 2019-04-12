// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemDependent;
import org.jetbrains.annotations.SystemIndependent;
import org.picocontainer.PicoContainer;

/**
 * @author peter
 */
final class DefaultProject implements ProjectEx {
  private static final Logger LOG = Logger.getInstance(DefaultProject.class);

  private final DefaultProjectTimed myDelegate = new DefaultProjectTimed(this);
  static final int DEFAULT_HASH_CODE = 4; // chosen by fair dice roll. guaranteed to be random. see https://xkcd.com/221/ for details.

  DefaultProject() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Created DefaultProject " + this, new Exception());
    }
  }

  @NotNull
  private Project getDelegate() {
    return myDelegate.get();
  }

  // make default project facade equal to any other default project facade
  // to enable Map<Project, T>
  @Override
  public boolean equals(Object o) {
    return o instanceof Project && ((Project)o).isDefault();
  }

  @Override
  public int hashCode() {
    return DEFAULT_HASH_CODE;
  }

  @Override
  public void dispose() {
    Disposer.dispose(myDelegate);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Disposed DefaultProject "+this);
    }
  }

  public boolean isCached() {
    return myDelegate.isCached();
  }

  @Override
  public void init() {

  }

  @Override
  public void setProjectName(@NotNull String name) {
    throw new IllegalStateException();
  }

  // delegates
  @Override
  @NotNull
  public String getName() {
    return getDelegate().getName();
  }

  @Override
  @Deprecated
  public VirtualFile getBaseDir() {
    return getDelegate().getBaseDir();
  }

  @Override
  @Nullable
  @SystemIndependent
  public String getBasePath() {
    return getDelegate().getBasePath();
  }

  @Override
  @Nullable
  public VirtualFile getProjectFile() {
    return getDelegate().getProjectFile();
  }

  @Override
  @Nullable
  @SystemIndependent
  public String getProjectFilePath() {
    return getDelegate().getProjectFilePath();
  }

  @Override
  @Nullable
  @SystemDependent
  public String getPresentableUrl() {
    return getDelegate().getPresentableUrl();
  }

  @Override
  @Nullable
  public VirtualFile getWorkspaceFile() {
    return getDelegate().getWorkspaceFile();
  }

  @Override
  @NotNull
  public String getLocationHash() {
    return getDelegate().getLocationHash();
  }

  @Override
  public void save() {
    getDelegate().save();
  }

  @Override
  public boolean isOpen() {
    return getDelegate().isOpen();
  }

  @Override
  public boolean isInitialized() {
    return getDelegate().isInitialized();
  }

  @Override
  public boolean isDefault() {
    return getDelegate().isDefault();
  }

  @Override
  @Deprecated
  public BaseComponent getComponent(@NotNull String name) {
    return getDelegate().getComponent(name);
  }

  @Override
  public <T> T getComponent(@NotNull Class<T> interfaceClass) {
    return getDelegate().getComponent(interfaceClass);
  }

  @Override
  public <T> T getComponent(@NotNull Class<T> interfaceClass, T defaultImplementationIfAbsent) {
    return getDelegate().getComponent(interfaceClass, defaultImplementationIfAbsent);
  }

  @Override
  public boolean hasComponent(@NotNull Class interfaceClass) {
    return getDelegate().hasComponent(interfaceClass);
  }

  @Override
  @NotNull
  @Deprecated
  public <T> T[] getComponents(@NotNull Class<T> baseClass) {
    return getDelegate().getComponents(baseClass);
  }

  @Override
  @NotNull
  public PicoContainer getPicoContainer() {
    return getDelegate().getPicoContainer();
  }

  @Override
  @NotNull
  public MessageBus getMessageBus() {
    return getDelegate().getMessageBus();
  }

  @Override
  public boolean isDisposed() {
    return getDelegate().isDisposed();
  }

  @Override
  @Deprecated
  @NotNull
  public <T> T[] getExtensions(@NotNull ExtensionPointName<T> extensionPointName) {
    return getDelegate().getExtensions(extensionPointName);
  }

  @Override
  @NotNull
  public Condition<?> getDisposed() {
    return getDelegate().getDisposed();
  }

  @Override
  @Nullable
  public <T> T getUserData(@NotNull Key<T> key) {
    return getDelegate().getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    getDelegate().putUserData(key, value);
  }
}
