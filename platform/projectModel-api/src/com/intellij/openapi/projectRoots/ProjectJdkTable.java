// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.EventListener;
import java.util.List;

public abstract class ProjectJdkTable {
  public static ProjectJdkTable getInstance() {
    return ServiceManager.getService(ProjectJdkTable.class);
  }

  @Nullable
  public abstract Sdk findJdk(String name);

  @Nullable
  public abstract Sdk findJdk(String name, String type);

  @NotNull
  public abstract Sdk[] getAllJdks();

  @NotNull
  public abstract List<Sdk> getSdksOfType(@NotNull SdkTypeId type);

  @Nullable
  public Sdk findMostRecentSdkOfType(@NotNull SdkTypeId type) {
    return getSdksOfType(type).stream().max(type.versionComparator()).orElse(null);
  }

  /** @deprecated comparing version strings across SDK types makes no sense; use {@link #findMostRecentSdkOfType} (to be removed in IDEA 2019) */
  @Deprecated
  public Sdk findMostRecentSdk(@NotNull Condition<Sdk> condition) {
    Sdk found = null;
    for (Sdk each : getAllJdks()) {
      if (condition.value(each) &&
          (found == null || Comparing.compare(each.getVersionString(), found.getVersionString()) > 0)) {
        found = each;
      }
    }
    return found;
  }

  public abstract void addJdk(@NotNull Sdk jdk);

  @TestOnly
  public void addJdk(@NotNull Sdk jdk, @NotNull Disposable parentDisposable) {
    addJdk(jdk);
    Disposer.register(parentDisposable, () -> WriteAction.runAndWait(()-> removeJdk(jdk)));
  }

  public abstract void removeJdk(@NotNull Sdk jdk);

  public abstract void updateJdk(@NotNull Sdk originalJdk, @NotNull Sdk modifiedJdk);

  public interface Listener extends EventListener {
    void jdkAdded(@NotNull Sdk jdk);
    void jdkRemoved(@NotNull Sdk jdk);
    void jdkNameChanged(@NotNull Sdk jdk, @NotNull String previousName);
  }

  public static class Adapter implements Listener {
    @Override public void jdkAdded(@NotNull Sdk jdk) { }
    @Override public void jdkRemoved(@NotNull Sdk jdk) { }
    @Override public void jdkNameChanged(@NotNull Sdk jdk, @NotNull String previousName) { }
  }

  /**
   * @deprecated use {@link ProjectJdkTable#JDK_TABLE_TOPIC} instead
   */
  @Deprecated
  public abstract void addListener(@NotNull Listener listener);

  /**
   * @deprecated use {@link ProjectJdkTable#JDK_TABLE_TOPIC} instead
   */
  @Deprecated
  public abstract void removeListener(@NotNull Listener listener);

  @NotNull
  public abstract SdkTypeId getDefaultSdkType();

  @NotNull
  public abstract SdkTypeId getSdkTypeByName(@NotNull String name);

  @NotNull
  public abstract Sdk createSdk(@NotNull String name, @NotNull SdkTypeId sdkType);

  public static final Topic<Listener> JDK_TABLE_TOPIC = Topic.create("Project JDK table", Listener.class);
}