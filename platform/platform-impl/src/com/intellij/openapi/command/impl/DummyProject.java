// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.pico.DefaultPicoContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

public final class DummyProject extends UserDataHolderBase implements Project {
  private static class DummyProjectHolder {
    private static final DummyProject ourInstance = new DummyProject();
  }

  @NotNull
  public static Project getInstance() {
    return DummyProjectHolder.ourInstance;
  }

  private DummyProject() { }

  @Override
  public VirtualFile getProjectFile() {
    return null;
  }

  @Override
  @NotNull
  public String getName() {
    return "";
  }

  @Override
  @NotNull
  public String getLocationHash() {
    return "dummy";
  }

  @Override
  @Nullable
  @SystemIndependent
  public String getProjectFilePath() {
    return null;
  }

  @Override
  public VirtualFile getWorkspaceFile() {
    return null;
  }

  @Override
  @Nullable
  public VirtualFile getBaseDir() {
    return null;
  }

  @Nullable
  @SystemIndependent
  @Override
  public String getBasePath() {
    return null;
  }

  @Override
  public void save() { }

  @Override
  public <T> T getService(@NotNull Class<T> serviceClass) {
    return null;
  }

  @Nullable
  @Override
  public <T> T getComponent(@NotNull Class<T> interfaceClass) {
    return null;
  }

  @Override
  @NotNull
  public DefaultPicoContainer getPicoContainer() {
    throw new UnsupportedOperationException("getPicoContainer is not implement in : " + getClass());
  }

  @NotNull
  @Override
  public ExtensionsArea getExtensionArea() {
    throw new UnsupportedOperationException("getExtensionArea is not implement in : " + getClass());
  }

  @Override
  public boolean isDisposed() {
    return false;
  }

  @Override
  @NotNull
  public Condition<?> getDisposed() {
    return o -> isDisposed();
  }

  @Override
  public boolean isOpen() {
    return false;
  }

  @Override
  public boolean isInitialized() {
    return false;
  }

  @NotNull
  @Override
  public MessageBus getMessageBus() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void dispose() { }
}