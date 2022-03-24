// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.diagnostic.ActivityCategory;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ExceptionUtilRt;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;
import org.picocontainer.PicoContainer;

import java.util.Map;

public final class DummyProject extends UserDataHolderBase implements Project {
  private static final class DummyProjectHolder {
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
  public <T> T @NotNull [] getComponents(@NotNull Class<T> baseClass) {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public PicoContainer getPicoContainer() {
    throw new UnsupportedOperationException("getPicoContainer is not implement in : " + getClass());
  }

  @Override
  public boolean isInjectionForExtensionSupported() {
    return false;
  }

  @NotNull
  @Override
  public ExtensionsArea getExtensionArea() {
    throw new UnsupportedOperationException("getExtensionArea is not implement in : " + getClass());
  }

  @Override
  public <T> T instantiateClassWithConstructorInjection(@NotNull Class<T> aClass,
                                                        @NotNull Object key,
                                                        @NotNull PluginId pluginId) {
    throw new UnsupportedOperationException();
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

  @Override
  public <T> @NotNull Class<T> loadClass(@NotNull String className, @NotNull PluginDescriptor pluginDescriptor) throws ClassNotFoundException {
    //noinspection unchecked
    return (Class<T>)Class.forName(className);
  }

  @Override
  public @NotNull ActivityCategory getActivityCategory(boolean isExtension) {
    return isExtension ? ActivityCategory.PROJECT_EXTENSION : ActivityCategory.PROJECT_SERVICE;
  }

  @Override
  public @NotNull RuntimeException createError(@NotNull @NonNls String message, @NotNull PluginId pluginId) {
    return new RuntimeException(message);
  }

  @Override
  public @NotNull RuntimeException createError(@NotNull @NonNls String message,
                                               @Nullable Throwable cause,
                                               @NotNull PluginId pluginId,
                                               @Nullable Map<String, String> attachments) {
    return new RuntimeException(message);
  }

  @Override
  public @NotNull RuntimeException createError(@NotNull Throwable error, @NotNull PluginId pluginId) {
    ExceptionUtilRt.rethrowUnchecked(error);
    return new RuntimeException(error);
  }
}