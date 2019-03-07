// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.persistence;

import com.intellij.openapi.components.*;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Date;
import java.util.Set;

@State(name = "FusApprovedGroupsCacheConfigurable", storages = @Storage(StoragePathMacros.CACHE_FILE))
public class ApprovedGroupsCacheConfigurable implements PersistentStateComponent<ApprovedGroupsCacheConfigurable.State> {

  public static ApprovedGroupsCacheConfigurable getInstance() {
    return ServiceManager.getService(ApprovedGroupsCacheConfigurable.class);
  }

  private State myState = new State();

  @NotNull
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  public static class State {
    private Set<String> myApprovedGroups = Collections.emptySet();
    private Date myLastUpdate = new Date(0);
    private String myBuild = "";

    @XCollection(propertyElementName = "approved_groups", elementName = "group")
    public Set<String> getApprovedGroups() {
      return myApprovedGroups;
    }

    public void setApprovedGroups(Set<String> approvedGroups) {
      myApprovedGroups = approvedGroups;
    }

    @Attribute("last_update")
    public Date getLastUpdate() {
      return myLastUpdate;
    }

    public void setLastUpdate(Date lastUpdate) {
      myLastUpdate = lastUpdate;
    }

    @Attribute("build")
    public String getBuild() {
      return myBuild;
    }

    public void setBuild(String build) {
      myBuild = build;
    }
  }

  /**
   * @return null if cache is stale
   */
  @Nullable
  public Set<String> getCachedGroups(@NotNull Date date, long cacheActualDuration, @Nullable BuildNumber currentBuild) {
    State state = getState();
    Date lastUpdate = state.getLastUpdate();
    if (date.getTime() - lastUpdate.getTime() > cacheActualDuration) return null;
    if (currentBuild != null) {
      BuildNumber cachedBuild = BuildNumber.fromStringOrNull(state.getBuild());
      if (cachedBuild != null && currentBuild.compareTo(cachedBuild) != 0) {
        return null;
      }
    }
    return state.getApprovedGroups();
  }

  public Set<String> getCachedGroups(Date date, long cacheActualDuration) {
    return getCachedGroups(date, cacheActualDuration, null);
  }

  public Set<String> cacheGroups(@NotNull Date date, @NotNull Set<String> groups, @NotNull BuildNumber build) {
    State state = getState();
    state.setApprovedGroups(groups);
    state.setLastUpdate(date);
    state.setBuild(build.asString());
    return groups;
  }
}
