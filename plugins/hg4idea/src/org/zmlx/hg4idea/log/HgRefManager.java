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
package org.zmlx.hg4idea.log;

import com.intellij.ui.JBColor;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.impl.SingletonRefGroup;
import com.intellij.vcs.log.impl.VcsLogUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class HgRefManager implements VcsLogRefManager {
  private static final Color CLOSED_BRANCH_COLOR = new JBColor(new Color(0xee7f8a), new Color(0xee7f8a).darker());
  private static final Color LOCAL_TAG_COLOR = JBColor.CYAN;
  private static final Color MQ_TAG_COLOR = new JBColor(new Color(0x1764ff), new Color(0x1764ff).darker());

  public static final VcsRefType TIP = new SimpleRefType(true, VcsLogStandardColors.Refs.TIP);
  public static final VcsRefType HEAD = new SimpleRefType(true, VcsLogStandardColors.Refs.LEAF);
  public static final VcsRefType BRANCH = new SimpleRefType(true, VcsLogStandardColors.Refs.BRANCH);
  public static final VcsRefType CLOSED_BRANCH = new SimpleRefType(false, CLOSED_BRANCH_COLOR);
  public static final VcsRefType BOOKMARK = new SimpleRefType(true, VcsLogStandardColors.Refs.BRANCH_REF);
  public static final VcsRefType TAG = new SimpleRefType(false, VcsLogStandardColors.Refs.TAG);
  public static final VcsRefType LOCAL_TAG = new SimpleRefType(false, LOCAL_TAG_COLOR);
  public static final VcsRefType MQ_APPLIED_TAG = new SimpleRefType(false, MQ_TAG_COLOR);
  
  // first has the highest priority
  private static final List<VcsRefType> REF_TYPE_PRIORITIES = Arrays.asList(TIP, HEAD, BRANCH, BOOKMARK, TAG);

  // -1 => higher priority
  public static final Comparator<VcsRefType> REF_TYPE_COMPARATOR = new Comparator<VcsRefType>() {
    @Override
    public int compare(VcsRefType type1, VcsRefType type2) {
      int p1 = REF_TYPE_PRIORITIES.indexOf(type1);
      int p2 = REF_TYPE_PRIORITIES.indexOf(type2);
      return p1 - p2;
    }
  };

  private static final String DEFAULT = "default";

  // @NotNull private final RepositoryManager<HgRepository> myRepositoryManager;

  // -1 => higher priority, i. e. the ref will be displayed at the left
  private final Comparator<VcsRef> REF_COMPARATOR = new Comparator<VcsRef>() {
    public int compare(VcsRef ref1, VcsRef ref2) {
      VcsRefType type1 = ref1.getType();
      VcsRefType type2 = ref2.getType();

      int typeComparison = REF_TYPE_COMPARATOR.compare(type1, type2);
      if (typeComparison != 0) {
        return typeComparison;
      }

      if (type1 == BRANCH) {
        if (ref1.getName().equals(DEFAULT)) {
          return -1;
        }
        if (ref2.getName().equals(DEFAULT)) {
          return 1;
        }
      }
      int nameComparison = ref1.getName().compareTo(ref2.getName());
      if (nameComparison != 0) {
        return nameComparison;
      }
      return VcsLogUtil.compareRoots(ref1.getRoot(), ref2.getRoot());
    }
  };

  @NotNull
  @Override
  public Comparator<VcsRef> getLabelsOrderComparator() {
    return REF_COMPARATOR;
  }

  @NotNull
  @Override
  public List<RefGroup> group(Collection<VcsRef> refs) {
    return ContainerUtil.map(sort(refs), new Function<VcsRef, RefGroup>() {
      @Override
      public RefGroup fun(final VcsRef ref) {
        return new SingletonRefGroup(ref);
      }
    });
  }

  @NotNull
  @Override
  public Comparator<VcsRef> getBranchLayoutComparator() {
    return REF_COMPARATOR;
  }

  @NotNull
  private Collection<VcsRef> sort(@NotNull Collection<VcsRef> refs) {
    return ContainerUtil.sorted(refs, getLabelsOrderComparator());
  }

  private static class SimpleRefType implements VcsRefType {
    private final boolean myIsBranch;
    @NotNull private final Color myColor;

    public SimpleRefType(boolean isBranch, @NotNull Color color) {
      myIsBranch = isBranch;
      myColor = color;
    }

    @Override
    public boolean isBranch() {
      return myIsBranch;
    }

    @NotNull
    @Override
    public Color getBackgroundColor() {
      return myColor;
    }
  }
}
