/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;

public class MockApplicationEx extends MockApplication implements ApplicationEx {
  public MockApplicationEx(@NotNull Disposable parentDisposable) {
    super(parentDisposable);
  }

  @NotNull
  @Override
  public String getName() {
    return "mock";
  }

  @Override
  public boolean holdsReadLock() {
    return false;
  }

  @Override
  public void load(String path) throws IOException, InvalidDataException {
  }

  @Override
  public boolean isLoaded() {
    return true;
  }

  @Override
  public void exit(boolean force) {
  }

  @Override
  public void restart(boolean force) {
  }

  @Override
  public void doNotSave() {
  }

  @Override
  public void doNotSave(boolean value) {
  }

  @Override
  public boolean isDoNotSave() {
    return false;
  }

  @Override
  public boolean runProcessWithProgressSynchronously(@NotNull final Runnable process, @NotNull final String progressTitle, final boolean canBeCanceled, @Nullable final Project project,
                                                     final JComponent parentComponent) {
    return false;
  }

  @Override
  public boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                                     @NotNull String progressTitle,
                                                     boolean canBeCanceled,
                                                     @Nullable Project project,
                                                     JComponent parentComponent,
                                                     String cancelText) {
    return false;
  }

  @Override
  public boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                                     @NotNull String progressTitle,
                                                     boolean canBeCanceled,
                                                     Project project) {
    return false;
  }

  @NotNull
  @Override
  public <T> T[] getExtensions(@NotNull final ExtensionPointName<T> extensionPointName) {
    return Extensions.getRootArea().getExtensionPoint(extensionPointName).getExtensions();
  }

  @Override
  public void assertIsDispatchThread(@Nullable final JComponent component) {
  }

  @Override
  public void assertTimeConsuming() {
  }

  @Override
  public void runEdtSafeAction(@NotNull Runnable runnable) {
    runnable.run();
  }

  @Override
  public boolean tryRunReadAction(@NotNull Runnable runnable) {
    return false;
  }
}
