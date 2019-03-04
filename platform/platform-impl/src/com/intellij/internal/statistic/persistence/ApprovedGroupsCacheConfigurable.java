// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.persistence;

import com.intellij.internal.statistic.service.fus.FUSWhitelist;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

@State(name = "FusApprovedGroupsCacheConfigurable", storages = @Storage(StoragePathMacros.CACHE_FILE))
public class ApprovedGroupsCacheConfigurable implements PersistentStateComponent<ApprovedGroupsCacheConfigurable.State> {
  private static final int CACHE_FORMAT = 1;

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
    private FUSWhitelist myWhitelist = FUSWhitelist.empty();
    private Date myLastUpdate = new Date(0);
    private String myBuild = "";
    private int myCacheFormatVersion = 0;

    @Tag("whitelist")
    public FUSWhitelist getWhitelist() {
      return myWhitelist;
    }

    public void setWhitelist(FUSWhitelist whitelist) {
      myWhitelist = whitelist;
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

    @Attribute("cache_version")
    public int getCacheFormatVersion() {
      return myCacheFormatVersion;
    }

    public void setCacheFormatVersion(int cacheFormatVersion) {
      myCacheFormatVersion = cacheFormatVersion;
    }
  }

  /**
   * @return null if cache is stale
   */
  @Nullable
  public FUSWhitelist getCachedGroups(@NotNull Date date, long cacheActualDuration, @Nullable BuildNumber currentBuild) {
    final State state = getState();
    if (state.getCacheFormatVersion() < CACHE_FORMAT) {
      // force update cache in old format
      return null;
    }

    Date lastUpdate = state.getLastUpdate();
    if (date.getTime() - lastUpdate.getTime() > cacheActualDuration) return null;
    if (currentBuild != null) {
      BuildNumber cachedBuild = BuildNumber.fromStringOrNull(state.getBuild());
      if (cachedBuild != null && currentBuild.compareTo(cachedBuild) != 0) {
        return null;
      }
    }
    return state.getWhitelist();
  }

  public FUSWhitelist getCachedGroups(Date date, long cacheActualDuration) {
    return getCachedGroups(date, cacheActualDuration, null);
  }

  public FUSWhitelist cacheGroups(@NotNull Date date, @NotNull FUSWhitelist whitelist, @NotNull BuildNumber build) {
    State state = getState();
    state.setCacheFormatVersion(CACHE_FORMAT);
    state.setWhitelist(whitelist);
    state.setLastUpdate(date);
    state.setBuild(build.asString());
    return whitelist;
  }
}
