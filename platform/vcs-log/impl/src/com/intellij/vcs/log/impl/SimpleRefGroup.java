/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.RefGroup;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsRefType;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;

public class SimpleRefGroup implements RefGroup {
  @NotNull private final String myName;
  @NotNull private final List<VcsRef> myRefs;

  public SimpleRefGroup(@NotNull String name, @NotNull List<VcsRef> refs) {
    myName = name;
    myRefs = refs;
  }

  @Override
  public boolean isExpanded() {
    return false;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public List<VcsRef> getRefs() {
    return myRefs;
  }

  @NotNull
  @Override
  public List<Color> getColors() {
    return getColors(myRefs);
  }

  @NotNull
  public static List<Color> getColors(@NotNull Collection<VcsRef> refs) {
    MultiMap<VcsRefType, VcsRef> referencesByType = ContainerUtil.groupBy(refs, VcsRef::getType);
    if (referencesByType.size() == 1) {
      Map.Entry<VcsRefType, Collection<VcsRef>> firstItem =
        ObjectUtils.assertNotNull(ContainerUtil.getFirstItem(referencesByType.entrySet()));
      boolean multiple = firstItem.getValue().size() > 1;
      Color color = firstItem.getKey().getBackgroundColor();
      return multiple ? Arrays.asList(color, color) : Collections.singletonList(color);
    }
    else {
      List<Color> colorsList = ContainerUtil.newArrayList();
      for (VcsRefType type : referencesByType.keySet()) {
        if (referencesByType.get(type).size() > 1) {
          colorsList.add(type.getBackgroundColor());
        }
        colorsList.add(type.getBackgroundColor());
      }
      return colorsList;
    }
  }

  public static void buildGroups(@NotNull MultiMap<VcsRefType, VcsRef> groupedRefs,
                                 boolean compact,
                                 boolean showTagNames,
                                 @NotNull List<RefGroup> result) {
    if (groupedRefs.isEmpty()) return;

    if (compact) {
      VcsRef firstRef = ObjectUtils.assertNotNull(ContainerUtil.getFirstItem(groupedRefs.values()));
      RefGroup group = ContainerUtil.getFirstItem(result);
      if (group == null) {
        result.add(new SimpleRefGroup(firstRef.getType().isBranch() || showTagNames ? firstRef.getName() : "",
                                      ContainerUtil.newArrayList(groupedRefs.values())));
      }
      else {
        group.getRefs().addAll(groupedRefs.values());
      }
    }
    else {
      for (Map.Entry<VcsRefType, Collection<VcsRef>> entry : groupedRefs.entrySet()) {
        if (entry.getKey().isBranch()) {
          for (VcsRef ref : entry.getValue()) {
            result.add(new SimpleRefGroup(ref.getName(), ContainerUtil.newArrayList(ref)));
          }
        }
        else {
          result.add(new SimpleRefGroup(showTagNames ? ObjectUtils.notNull(ContainerUtil.getFirstItem(entry.getValue())).getName() : "",
                                        ContainerUtil.newArrayList(entry.getValue())));
        }
      }
    }
  }
}
