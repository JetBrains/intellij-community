// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.ReflectionUtil;
import com.intellij.util.messages.MessageBus;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.GlobalScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import java.util.Map;

public final class DummyProject extends UserDataHolderBase implements Project {
  private static final class DummyProjectHolder {
    private static final DummyProject ourInstance = new DummyProject();
  }

  public static @NotNull Project getInstance() {
    return DummyProjectHolder.ourInstance;
  }

  private DummyProject() { }

  @Override
  public VirtualFile getProjectFile() {
    return null;
  }

  @Override
  public @NotNull String getName() {
    return "";
  }

  @Override
  public @NotNull String getLocationHash() {
    return "dummy";
  }

  @Override
  public @Nullable @SystemIndependent String getProjectFilePath() {
    return null;
  }

  @Override
  public VirtualFile getWorkspaceFile() {
    return null;
  }

  @Override
  public @Nullable VirtualFile getBaseDir() {
    return null;
  }

  @Override
  public @Nullable @SystemIndependent String getBasePath() {
    return null;
  }

  @Override
  public void save() { }

  @Override
  public <T> T getService(@NotNull Class<T> serviceClass) {
    return null;
  }

  @Override
  public @Nullable <T> T getComponent(@NotNull Class<T> interfaceClass) {
    return null;
  }

  @Override
  public boolean hasComponent(@NotNull Class<?> interfaceClass) {
    return false;
  }

  @Override
  public boolean isInjectionForExtensionSupported() {
    return false;
  }

  @Override
  public @NotNull ExtensionsArea getExtensionArea() {
    throw new UnsupportedOperationException("getExtensionArea is not implement in : " + getClass());
  }

  @Override
  public <T> T instantiateClassWithConstructorInjection(@NotNull Class<T> aClass,
                                                        @NotNull Object key,
                                                        @NotNull PluginId pluginId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T instantiateClass(@NotNull Class<T> aClass, @NotNull PluginId pluginId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isDisposed() {
    return false;
  }

  @Override
  public @NotNull Condition<?> getDisposed() {
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

  @Override
  public CoroutineScope getCoroutineScope() {
    return GlobalScope.INSTANCE;
  }

  @Override
  public @NotNull MessageBus getMessageBus() {
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
  public <T> @NotNull T instantiateClass(@NotNull String className, @NotNull PluginDescriptor pluginDescriptor) {
    try {
      return ReflectionUtil.newInstance(loadClass(className, pluginDescriptor));
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
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