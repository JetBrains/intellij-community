// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.RefGroup;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsRefType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.*;

public class SimpleRefGroup implements RefGroup {
  @NotNull private final @Nls String myName;
  @NotNull private final List<VcsRef> myRefs;
  private final boolean myExpanded;

  public SimpleRefGroup(@NotNull @Nls String name, @NotNull List<VcsRef> refs) {
    this(name, refs, false);
  }

  public SimpleRefGroup(@NotNull @Nls String name, @NotNull List<VcsRef> refs, boolean expanded) {
    myName = name;
    myRefs = refs;
    myExpanded = expanded;
  }

  @Override
  public boolean isExpanded() {
    return myExpanded;
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
  public static List<Color> getColors(@NotNull Collection<? extends VcsRef> refs) {
    MultiMap<VcsRefType, VcsRef> referencesByType = ContainerUtil.groupBy(refs, VcsRef::getType);
    if (referencesByType.size() == 1) {
      Map.Entry<VcsRefType, Collection<VcsRef>> firstItem =
        Objects.requireNonNull(ContainerUtil.getFirstItem(referencesByType.entrySet()));
      boolean multiple = firstItem.getValue().size() > 1;
      Color color = firstItem.getKey().getBackgroundColor();
      return multiple ? Arrays.asList(color, color) : Collections.singletonList(color);
    }
    else {
      List<Color> colorsList = new ArrayList<>();
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
      VcsRef firstRef = Objects.requireNonNull(ContainerUtil.getFirstItem(groupedRefs.values()));
      RefGroup group = ContainerUtil.getFirstItem(result);
      if (group == null) {
        result.add(new SimpleRefGroup(firstRef.getType().isBranch() || showTagNames ? firstRef.getName() : "",
                                      new ArrayList<>(groupedRefs.values())));
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
          result.add(new SimpleRefGroup(showTagNames ? Objects.requireNonNull(ContainerUtil.getFirstItem(entry.getValue())).getName() : "",
                                        new ArrayList<>(entry.getValue())));
        }
      }
    }
  }
}
