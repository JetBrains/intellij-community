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

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.RefGroup;
import com.intellij.vcs.log.VcsLogRefManager;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsRefType;
import com.intellij.vcs.log.impl.SimpleRefGroup;
import com.intellij.vcs.log.impl.SingletonRefGroup;
import com.intellij.vcs.log.impl.VcsLogUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgColors;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

public class HgRefManager implements VcsLogRefManager {

  public static final VcsRefType TIP = new SimpleRefType("TIP", true, HgColors.REFS_TIP);
  public static final VcsRefType HEAD = new SimpleRefType("HEAD", true, HgColors.REFS_HEAD);
  public static final VcsRefType BRANCH = new SimpleRefType("BRANCH", true, HgColors.REFS_BRANCH);
  public static final VcsRefType CLOSED_BRANCH = new SimpleRefType("CLOSED_BRANCH", false, HgColors.CLOSED_BRANCH);
  public static final VcsRefType BOOKMARK = new SimpleRefType("BOOKMARK", true, HgColors.REFS_BOOKMARK);
  public static final VcsRefType TAG = new SimpleRefType("TAG", false, HgColors.REFS_TAG);
  public static final VcsRefType LOCAL_TAG = new SimpleRefType("LOCAL_TAG", false, HgColors.LOCAL_TAG);
  public static final VcsRefType MQ_APPLIED_TAG = new SimpleRefType("MQ_TAG", false, HgColors.MQ_TAG);

  // first has the highest priority
  private static final List<VcsRefType> REF_TYPE_PRIORITIES = Arrays.asList(TIP, HEAD, BRANCH, BOOKMARK, TAG);
  private static final List<VcsRefType> REF_TYPE_INDEX =
    Arrays.asList(TIP, HEAD, BRANCH, CLOSED_BRANCH, BOOKMARK, TAG, LOCAL_TAG, MQ_APPLIED_TAG);

  // -1 => higher priority
  public static final Comparator<VcsRefType> REF_TYPE_COMPARATOR = (type1, type2) -> {
    int p1 = REF_TYPE_PRIORITIES.indexOf(type1);
    int p2 = REF_TYPE_PRIORITIES.indexOf(type2);
    return p1 - p2;
  };

  public static final String DEFAULT = "default";

  // @NotNull private final RepositoryManager<HgRepository> myRepositoryManager;

  // -1 => higher priority, i. e. the ref will be displayed at the left
  private final Comparator<VcsRef> REF_COMPARATOR = (ref1, ref2) -> {
    VcsRefType type1 = ref1.getType();
    VcsRefType type2 = ref2.getType();

    int typeComparison = REF_TYPE_COMPARATOR.compare(type1, type2);
    if (typeComparison != 0) {
      return typeComparison;
    }

    int nameComparison = ref1.getName().compareTo(ref2.getName());
    if (nameComparison != 0) {
      if (type1 == BRANCH) {
        if (ref1.getName().equals(DEFAULT)) {
          return -1;
        }
        if (ref2.getName().equals(DEFAULT)) {
          return 1;
        }
      }
      return nameComparison;
    }

    return VcsLogUtil.compareRoots(ref1.getRoot(), ref2.getRoot());
  };

  @NotNull
  @Override
  public Comparator<VcsRef> getLabelsOrderComparator() {
    return REF_COMPARATOR;
  }

  @NotNull
  @Override
  public List<RefGroup> groupForBranchFilter(@NotNull Collection<VcsRef> refs) {
    return ContainerUtil.map(sort(refs), ref -> new SingletonRefGroup(ref));
  }

  @NotNull
  @Override
  public List<RefGroup> groupForTable(@NotNull Collection<VcsRef> references, boolean compact, boolean showTagNames) {
    List<VcsRef> sortedReferences = sort(references);

    List<VcsRef> headAndTip = ContainerUtil.newArrayList();
    MultiMap<VcsRefType, VcsRef> groupedRefs = MultiMap.createLinked();
    for (VcsRef ref : sortedReferences) {
      if (ref.getType().equals(HEAD) || ref.getType().equals(TIP)) {
        headAndTip.add(ref);
      }
      else {
        groupedRefs.putValue(ref.getType(), ref);
      }
    }

    List<RefGroup> result = ContainerUtil.newArrayList();
    SimpleRefGroup.buildGroups(groupedRefs, compact, showTagNames, result);
    RefGroup firstGroup = ContainerUtil.getFirstItem(result);
    if (firstGroup != null) {
      firstGroup.getRefs().addAll(0, headAndTip);
    }
    else {
      result.add(new SimpleRefGroup("", headAndTip));
    }

    return result;
  }

  @Override
  public void serialize(@NotNull DataOutput out, @NotNull VcsRefType type) throws IOException {
    out.writeInt(REF_TYPE_INDEX.indexOf(type));
  }

  @NotNull
  @Override
  public VcsRefType deserialize(@NotNull DataInput in) throws IOException {
    int id = in.readInt();
    if (id < 0 || id > REF_TYPE_INDEX.size() - 1) throw new IOException("Reference type by id " + id + " does not exist");
    return REF_TYPE_INDEX.get(id);
  }

  @NotNull
  @Override
  public Comparator<VcsRef> getBranchLayoutComparator() {
    return REF_COMPARATOR;
  }

  @NotNull
  private List<VcsRef> sort(@NotNull Collection<VcsRef> refs) {
    return ContainerUtil.sorted(refs, getLabelsOrderComparator());
  }

  private static class SimpleRefType implements VcsRefType {
    @NotNull private final String myName;
    private final boolean myIsBranch;
    @NotNull private final ColorKey myColor;

    public SimpleRefType(@NotNull String name, boolean isBranch, @NotNull ColorKey color) {
      myName = name;
      myIsBranch = isBranch;
      myColor = color;
    }

    @Override
    public boolean isBranch() {
      return myIsBranch;
    }

    @NotNull
    @Override
    public ColorKey getBgColorKey() {
      return myColor;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      SimpleRefType type = (SimpleRefType)o;
      return myIsBranch == type.myIsBranch && Objects.equals(myName, type.myName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myName, myIsBranch);
    }
  }
}
