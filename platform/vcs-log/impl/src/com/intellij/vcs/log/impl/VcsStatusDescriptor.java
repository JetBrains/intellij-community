/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.log.impl;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.openapi.vcs.changes.Change.Type.MODIFICATION;
import static com.intellij.util.ObjectUtils.notNull;

public abstract class VcsStatusDescriptor<S> {
  @NotNull
  public List<MergedStatusInfo<S>> getMergedStatusInfo(@NotNull List<List<S>> statuses) {
    List<S> firstParent = statuses.get(0);
    if (statuses.size() == 1) return ContainerUtil.map(firstParent, info -> new MergedStatusInfo<>(info));

    List<Map<String, S>> affectedMap =
      ContainerUtil.map(statuses, infos -> {
        LinkedHashMap<String, S> map = ContainerUtil.newLinkedHashMap();

        for (S info : infos) {
          String path = getPath(info);
          if (path != null) map.put(path, info);
        }

        return map;
      });

    List<MergedStatusInfo<S>> result = ContainerUtil.newArrayList();

    outer:
    for (String path : affectedMap.get(0).keySet()) {
      List<S> statusesList = ContainerUtil.newArrayList();
      for (Map<String, S> infoMap : affectedMap) {
        S status = infoMap.get(path);
        if (status == null) continue outer;
        statusesList.add(status);
      }

      result.add(new MergedStatusInfo<>(getMergedStatusInfo(path, statusesList), statusesList));
    }

    return result;
  }

  @NotNull
  private S getMergedStatusInfo(@NotNull String path, @NotNull List<S> statuses) {
    Set<Change.Type> types = ContainerUtil.map2Set(statuses, this::getType);

    if (types.size() == 1) {
      Change.Type type = notNull(ContainerUtil.getFirstItem(types));
      if (type.equals(Change.Type.MOVED)) {
        String renamedFrom = null;
        for (S status : statuses) {
          if (renamedFrom == null) {
            renamedFrom = getFirstPath(status);
          }
          else if (!renamedFrom.equals(getFirstPath(status))) {
            return createStatus(MODIFICATION, path, null);
          }
        }
      }
      return statuses.get(0);
    }

    if (types.contains(Change.Type.DELETED)) return createStatus(Change.Type.DELETED, path, null);
    return createStatus(MODIFICATION, path, null);
  }

  @Nullable
  private String getPath(@NotNull S info) {
    switch (getType(info)) {
      case MODIFICATION:
      case NEW:
      case DELETED:
        return getFirstPath(info);
      case MOVED:
        return getSecondPath(info);
    }
    return null;
  }

  @NotNull
  protected abstract S createStatus(@NotNull Change.Type type, @NotNull String path, @Nullable String secondPath);

  @NotNull
  public abstract String getFirstPath(@NotNull S info);

  @Nullable
  public abstract String getSecondPath(@NotNull S info);

  @NotNull
  public abstract Change.Type getType(@NotNull S info);


  public static class MergedStatusInfo<S> {
    @NotNull private final S myStatusInfo;
    @NotNull private final List<S> myMergedStatusInfos;

    public MergedStatusInfo(@NotNull S info, @NotNull List<S> infos) {
      myStatusInfo = info;
      myMergedStatusInfos = new SmartList<>(infos);
    }

    public MergedStatusInfo(@NotNull S info) {
      this(info, ContainerUtil.emptyList());
    }

    @NotNull
    public S getStatusInfo() {
      return myStatusInfo;
    }

    @NotNull
    public List<S> getMergedStatusInfos() {
      return myMergedStatusInfos;
    }

    @Override
    public String toString() {
      return "MergedStatusInfo{" +
             "myStatusInfo=" + myStatusInfo +
             ", myMergedStatusInfos=" + myMergedStatusInfos +
             '}';
    }
  }
}
