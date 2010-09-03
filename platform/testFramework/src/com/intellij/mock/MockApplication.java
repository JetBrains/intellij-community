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
package com.intellij.mock;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.application.ModalityInvokator;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.impl.ModalityStateEx;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class MockApplication extends MockComponentManager implements ApplicationEx {
  private ModalityState MODALITY_STATE_NONE;
  public MockApplication() {
    super(null);
  }

  public String getName() {
    return "mock";
  }

  public boolean holdsReadLock() {
    return false;
  }

  public void load(String path) throws IOException, InvalidDataException {
  }

  public boolean isInternal() {
    return false;
  }

  public boolean isDispatchThread() {
    return true;
  }

  public boolean isActive() {
    return true;
  }

  public void exit(boolean force) {
  }

  public void assertReadAccessAllowed() {
  }

  public void assertWriteAccessAllowed() {
  }

  public boolean isReadAccessAllowed() {
    return true;
  }

  public boolean isWriteAccessAllowed() {
    return true;
  }

  public boolean isUnitTestMode() {
    return true;
  }

  public boolean isHeadlessEnvironment() {
    return true;
  }

  public boolean isCommandLine() {
    return true;
  }

  public IdeaPluginDescriptor getPlugin(PluginId id) {
    return null;
  }

  public IdeaPluginDescriptor[] getPlugins() {
    return new IdeaPluginDescriptor[0];
  }


  public Future<?> executeOnPooledThread(@NotNull Runnable action) {
    new Thread(action).start();
    return null; // ?
  }

  public boolean isDisposeInProgress() {
    return false;
  }

  public boolean isRestartCapable() {
    return false;
  }

  public void restart() {
  }

  public void runReadAction(@NotNull Runnable action) {
    action.run();
  }

  public <T> T runReadAction(@NotNull Computable<T> computation) {
    return computation.compute();
  }

  public void runWriteAction(@NotNull Runnable action) {
    action.run();
  }

  public <T> T runWriteAction(@NotNull Computable<T> computation) {
    return computation.compute();
  }

  public <T> T getCurrentWriteAction(@NotNull Class<T> actionClass) {
    return null;
  }

  public void assertIsDispatchThread() {
  }

  public void addApplicationListener(@NotNull ApplicationListener listener) {
  }

  public void addApplicationListener(@NotNull ApplicationListener listener, @NotNull Disposable parent) {
  }

  public void removeApplicationListener(@NotNull ApplicationListener listener) {
  }

  public void saveAll() {
  }

  public void saveSettings() {
  }

  public void exit() {
  }

  public void assertReadAccessToDocumentsAllowed() {
  }

  public void doNotSave() {
  }

  public boolean isDoNotSave() {
    return false; 
  }

  public boolean runProcessWithProgressSynchronously(final Runnable process, final String progressTitle, final boolean canBeCanceled, @Nullable final Project project,
                                                     final JComponent parentComponent) {
    return false;
  }

  public boolean runProcessWithProgressSynchronously(Runnable process,
                                                     String progressTitle,
                                                     boolean canBeCanceled,
                                                     @Nullable Project project,
                                                     JComponent parentComponent,
                                                     String cancelText) {
    return false;
  }

  public boolean runProcessWithProgressSynchronously(Runnable process,
                                                     String progressTitle,
                                                     boolean canBeCanceled,
                                                     Project project) {
    return false;
  }

  public boolean isInModalProgressThread() {
    return false;
  }


  public void invokeLater(@NotNull final Runnable runnable, @NotNull final Condition expired) {
  }

  public void invokeLater(@NotNull final Runnable runnable, @NotNull final ModalityState state, @NotNull final Condition expired) {
  }

  public void invokeLater(@NotNull Runnable runnable) {
  }

  public void invokeLater(@NotNull Runnable runnable, @NotNull ModalityState state) {
  }

  @NotNull
  public ModalityInvokator getInvokator() {
    throw new UnsupportedOperationException();
  }

  public void invokeAndWait(@NotNull Runnable runnable, @NotNull ModalityState modalityState) {
  }

  public long getStartTime() {
    return 0;
  }

  public long getIdleTime() {
    return 0;
  }

  public <T> T[] getExtensions(final ExtensionPointName<T> extensionPointName) {
    return Extensions.getRootArea().getExtensionPoint(extensionPointName).getExtensions();
  }

  public ModalityState getCurrentModalityState() {
    return null;
  }

  public ModalityState getModalityStateForComponent(@NotNull Component c) {
    return null;
  }

  public ModalityState getDefaultModalityState() {
    return null;
  }

  public ModalityState getNoneModalityState() {
    if (MODALITY_STATE_NONE == null) {
      MODALITY_STATE_NONE = new ModalityStateEx(ArrayUtil.EMPTY_OBJECT_ARRAY);
    }
    return MODALITY_STATE_NONE;
  }

  public <T> List<Future<T>> invokeAllUnderReadAction(@NotNull final Collection<Callable<T>> tasks, final ExecutorService executorService)
    throws Throwable {
    return null;
  }

  public void assertIsDispatchThread(@Nullable final JComponent component) {
  }

  public void assertTimeConsuming() {
  }

  public void runEdtSafeAction(@NotNull Runnable runnable) {
    runnable.run();
  }

  public boolean tryRunReadAction(@NotNull Runnable runnable) {
    return false;
  }
}
