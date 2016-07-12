/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public abstract Sdk[] getAllJdks();

  public abstract List<Sdk> getSdksOfType(SdkTypeId type);

  @Nullable
  public Sdk findMostRecentSdkOfType(final SdkTypeId type) {
    return findMostRecentSdk(sdk -> sdk.getSdkType() == type);
  }

  @Nullable
  public Sdk findMostRecentSdk(Condition<Sdk> condition) {
    Sdk found = null;
    for (Sdk each : getAllJdks()) {
      if (!condition.value(each)) continue;
      if (found == null) {
        found = each;
        continue;
      }
      if (Comparing.compare(each.getVersionString(), found.getVersionString()) > 0) found = each;
    }
    return found;
  }

  public abstract void addJdk(Sdk jdk);

  public abstract void removeJdk(Sdk jdk);

  public abstract void updateJdk(Sdk originalJdk, Sdk modifiedJdk);

  public interface Listener extends EventListener {
    void jdkAdded(Sdk jdk);
    void jdkRemoved(Sdk jdk);
    void jdkNameChanged(Sdk jdk, String previousName);
  }

  public static class Adapter implements Listener {
    @Override public void jdkAdded(Sdk jdk) { }
    @Override public void jdkRemoved(Sdk jdk) { }
    @Override public void jdkNameChanged(Sdk jdk, String previousName) { }
  }

  /**
   * @deprecated use {@link ProjectJdkTable#JDK_TABLE_TOPIC} instead
   */
  public abstract void addListener(Listener listener);

  /**
   * @deprecated use {@link ProjectJdkTable#JDK_TABLE_TOPIC} instead
   */
  public abstract void removeListener(Listener listener);

  public abstract SdkTypeId getDefaultSdkType();

  public abstract SdkTypeId getSdkTypeByName(@NotNull String name);

  public abstract Sdk createSdk(final String name, final SdkTypeId sdkType);

  public static final Topic<Listener> JDK_TABLE_TOPIC = Topic.create("Project JDK table", Listener.class);
}
