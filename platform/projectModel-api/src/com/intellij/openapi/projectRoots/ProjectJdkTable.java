// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.EventListener;
import java.util.List;

/**
 * Provides access to SDKs configured in the IDE. Despite its name, this class operates on SDKs of all kinds, not only on Java SDKs.
 */
@ApiStatus.NonExtendable
public abstract class ProjectJdkTable {
  public static ProjectJdkTable getInstance() {
    return ApplicationManager.getApplication().getService(ProjectJdkTable.class);
  }

  public abstract @Nullable Sdk findJdk(@NotNull String name);

  public abstract @Nullable Sdk findJdk(@NotNull String name, @NotNull String type);

  public abstract Sdk @NotNull [] getAllJdks();

  public abstract @NotNull List<Sdk> getSdksOfType(@NotNull SdkTypeId type);

  public @Nullable Sdk findMostRecentSdkOfType(@NotNull SdkTypeId type) {
    return getSdksOfType(type).stream().max(type.versionComparator()).orElse(null);
  }

  @RequiresWriteLock
  public abstract void addJdk(@NotNull Sdk jdk);

  @TestOnly
  public void addJdk(@NotNull Sdk jdk, @NotNull Disposable parentDisposable) {
    Sdk existingJdk = findJdk(jdk.getName(), jdk.getSdkType().getName());
    if (existingJdk == null || existingJdk.getSdkAdditionalData() != jdk.getSdkAdditionalData()) {
      addJdk(jdk);
    }
    // Anyway we need to call remove method otherwise the created VirtualFilePonters will not be removed
    Disposer.register(parentDisposable, () -> WriteAction.runAndWait(()-> removeJdk(jdk)));
  }

  @RequiresWriteLock
  public abstract void removeJdk(@NotNull Sdk jdk);

  @RequiresWriteLock
  public abstract void updateJdk(@NotNull Sdk originalJdk, @NotNull Sdk modifiedJdk);

  public interface Listener extends EventListener {
    default void jdkAdded(@NotNull Sdk jdk) {
    }

    default void jdkRemoved(@NotNull Sdk jdk) {
    }

    default void jdkNameChanged(@NotNull Sdk jdk, @NotNull String previousName) {
    }
  }

  public abstract @NotNull SdkTypeId getDefaultSdkType();

  public abstract @NotNull SdkTypeId getSdkTypeByName(@NotNull String name);

  public abstract @NotNull Sdk createSdk(@NotNull String name, @NotNull SdkTypeId sdkType);

  /**
   * This method may automatically detect Sdk if none are configured.
   */
  public void preconfigure() {
  }

  @TestOnly
  public void saveOnDisk() { }

  @Topic.AppLevel
  public static final Topic<Listener> JDK_TABLE_TOPIC = new Topic<>(Listener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN);
}