// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.log;

import com.intellij.dvcs.repo.RepositoryManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.impl.SimpleRefGroup;
import com.intellij.vcs.log.impl.SimpleRefType;
import com.intellij.vcs.log.impl.SingletonRefGroup;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.branch.HgBranchManager;
import org.zmlx.hg4idea.branch.HgBranchType;
import org.zmlx.hg4idea.repo.HgRepository;

import java.awt.*;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;
import java.util.*;

import static com.intellij.ui.JBColor.namedColor;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public class HgRefManager implements VcsLogRefManager {
  private static final JBColor TIP_COLOR = namedColor("VersionControl.HgLog.tipIconColor", VcsLogStandardColors.Refs.TIP);
  private static final JBColor HEAD_COLOR = namedColor("VersionControl.HgLog.headIconColor", VcsLogStandardColors.Refs.LEAF);
  private static final JBColor BRANCH_COLOR = namedColor("VersionControl.HgLog.branchIconColor", VcsLogStandardColors.Refs.BRANCH);
  private static final JBColor CLOSED_BRANCH_COLOR = namedColor("VersionControl.HgLog.closedBranchIconColor",
                                                                new JBColor(new Color(0x823139), new Color(0xff5f6f)));
  private static final JBColor BOOKMARK_COLOR = namedColor("VersionControl.HgLog.bookmarkIconColor", VcsLogStandardColors.Refs.BRANCH_REF);
  private static final JBColor TAG_COLOR = namedColor("VersionControl.HgLog.tagIconColor", VcsLogStandardColors.Refs.TAG);
  private static final JBColor LOCAL_TAG_COLOR = namedColor("VersionControl.HgLog.localTagIconColor",
                                                            new JBColor(new Color(0x009090), new Color(0x00f3f3)));
  private static final JBColor MQ_TAG_COLOR = namedColor("VersionControl.HgLog.mqTagIconColor",
                                                         new JBColor(new Color(0x002f90), new Color(0x0055ff)));

  public static final VcsRefType TIP = new SimpleRefType("TIP", true, TIP_COLOR);
  public static final VcsRefType HEAD = new SimpleRefType("HEAD", true, HEAD_COLOR);
  public static final VcsRefType BRANCH = new SimpleRefType("BRANCH", true, BRANCH_COLOR);
  public static final VcsRefType CLOSED_BRANCH = new SimpleRefType("CLOSED_BRANCH", false, CLOSED_BRANCH_COLOR);
  public static final VcsRefType BOOKMARK = new SimpleRefType("BOOKMARK", true, BOOKMARK_COLOR);
  public static final VcsRefType TAG = new SimpleRefType("TAG", false, TAG_COLOR);
  public static final VcsRefType LOCAL_TAG = new SimpleRefType("LOCAL_TAG", false, LOCAL_TAG_COLOR);
  public static final VcsRefType MQ_APPLIED_TAG = new SimpleRefType("MQ_TAG", false, MQ_TAG_COLOR);

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

  @NotNull private final HgBranchManager myBranchManager;
  @NotNull private final RepositoryManager<HgRepository> myRepositoryManager;

  public HgRefManager(@NotNull Project project, @NotNull RepositoryManager<HgRepository> repositoryManager) {
    myRepositoryManager = repositoryManager;
    myBranchManager = ServiceManager.getService(project, HgBranchManager.class);
  }

  @NotNull
  @Override
  public Comparator<VcsRef> getLabelsOrderComparator() {
    return REF_COMPARATOR;
  }

  @NotNull
  @Override
  public List<RefGroup> groupForBranchFilter(@NotNull Collection<? extends VcsRef> refs) {
    List<VcsRef> sortedRefs = sort(refs);
    MultiMap<VcsRefType, VcsRef> groupedRefs = ContainerUtil.groupBy(sortedRefs, VcsRef::getType);

    List<RefGroup> result = new ArrayList<>();
    List<VcsRef> branches = new ArrayList<>();
    List<VcsRef> bookmarks = new ArrayList<>();
    for (Map.Entry<VcsRefType, Collection<VcsRef>> entry : groupedRefs.entrySet()) {
      if (entry.getKey().equals(TIP) || entry.getKey().equals(HEAD)) {
        for (VcsRef ref : entry.getValue()) {
          result.add(new SingletonRefGroup(ref));
        }
      }
      else if (entry.getKey().equals(BOOKMARK)) {
        bookmarks.addAll(entry.getValue());
      }
      else {
        branches.addAll(entry.getValue());
      }
    }

    if (!branches.isEmpty()) result.add(new SimpleRefGroup(HgBundle.message("hg.ref.group.name.branches"), branches, false));
    if (!bookmarks.isEmpty()) result.add(new SimpleRefGroup(HgBundle.message("hg.ref.group.name.bookmarks"), bookmarks, false));

    return result;
  }

  @NotNull
  @Override
  public List<RefGroup> groupForTable(@NotNull Collection<? extends VcsRef> references, boolean compact, boolean showTagNames) {
    List<VcsRef> sortedReferences = sort(references);

    List<VcsRef> headAndTip = new ArrayList<>();
    MultiMap<VcsRefType, VcsRef> groupedRefs = MultiMap.createLinked();
    for (VcsRef ref : sortedReferences) {
      if (ref.getType().equals(HEAD) || ref.getType().equals(TIP)) {
        headAndTip.add(ref);
      }
      else {
        groupedRefs.putValue(ref.getType(), ref);
      }
    }

    List<RefGroup> result = new ArrayList<>();
    SimpleRefGroup.buildGroups(groupedRefs, compact, showTagNames, result);
    RefGroup firstGroup = getFirstItem(result);
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
  private static HgBranchType getBranchType(@NotNull VcsRef reference) {
    return reference.getType().equals(BOOKMARK) ? HgBranchType.BOOKMARK : HgBranchType.BRANCH;
  }

  @Nullable
  @CalledInAny
  private HgRepository getRepository(@NotNull VcsRef reference) {
    return myRepositoryManager.getRepositoryForRootQuick(reference.getRoot());
  }

  @Override
  public boolean isFavorite(@NotNull VcsRef reference) {
    if (reference.getType().equals(HEAD) || reference.getType().equals(TIP)) return true;
    if (!reference.getType().isBranch()) return false;
    return myBranchManager.isFavorite(getBranchType(reference), getRepository(reference), reference.getName());
  }

  @Override
  public void setFavorite(@NotNull VcsRef reference, boolean favorite) {
    if (!reference.getType().isBranch() || reference.getType().equals(HEAD) || reference.getType().equals(TIP)) return;
    myBranchManager.setFavorite(getBranchType(reference), getRepository(reference), reference.getName(), favorite);
  }

  @NotNull
  @Override
  public Comparator<VcsRef> getBranchLayoutComparator() {
    return REF_COMPARATOR;
  }

  @NotNull
  private List<VcsRef> sort(@NotNull Collection<? extends VcsRef> refs) {
    return ContainerUtil.sorted(refs, getLabelsOrderComparator());
  }
}
