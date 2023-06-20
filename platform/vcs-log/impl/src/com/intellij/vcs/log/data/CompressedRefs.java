// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.vcs.log.VcsRef;
import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CompressedRefs {
  private static final Logger LOG = Logger.getInstance(CompressedRefs.class);

  private final @NotNull VcsLogStorage myStorage;

  // maps each commit id to the list of tag ids on this commit
  private final @NotNull Int2ObjectMap<IntArrayList> myTags = new Int2ObjectOpenHashMap<>();
  // maps each commit id to the list of branches on this commit
  private final @NotNull Int2ObjectMap<List<VcsRef>> myBranches = new Int2ObjectOpenHashMap<>();

  public CompressedRefs(@NotNull Set<VcsRef> refs, @NotNull VcsLogStorage storage) {
    myStorage = storage;
    VirtualFile root = null;
    for (VcsRef ref : refs) {
      assert root == null || root.equals(ref.getRoot()) : "All references are supposed to be from the single root";
      root = ref.getRoot();

      int index = myStorage.getCommitIndex(ref.getCommitHash(), ref.getRoot());
      if (ref.getType().isBranch()) {
        myBranches.computeIfAbsent(index, key -> new SmartList<>()).add(ref);
      }
      else {
        int refIndex = myStorage.getRefIndex(ref);
        if (refIndex != VcsLogStorageImpl.NO_INDEX) {
          myTags.computeIfAbsent(index, key -> new IntArrayList()).add(refIndex);
        }
      }
    }
    //noinspection SSBasedInspection
    for (IntArrayList list : myTags.values()) {
      list.trim();
    }
  }

  boolean contains(int index) {
    return myBranches.containsKey(index) || myTags.containsKey(index);
  }

  @NotNull
  SmartList<VcsRef> refsToCommit(int index) {
    SmartList<VcsRef> result = new SmartList<>();
    if (myBranches.containsKey(index)) result.addAll(myBranches.get(index));
    IntList tags = myTags.get(index);
    if (tags != null) {
      tags.forEach(tag -> {
        VcsRef ref = myStorage.getVcsRef(tag);
        if (ref != null) {
          result.add(ref);
        } else {
          LOG.error("Could not find a tag by id " + tag + " at commit " + myStorage.getCommitId(index));
        }
      });
    }
    return result;
  }

  public @NotNull Stream<VcsRef> streamBranches() {
    return myBranches.values().stream().flatMap(Collection::stream);
  }

  private @NotNull Stream<VcsRef> streamTags() {
    return myTags.values().stream().flatMapToInt(IntCollection::intStream).mapToObj(myStorage::getVcsRef);
  }

  public @NotNull Stream<VcsRef> stream() {
    return Stream.concat(streamBranches(), streamTags());
  }

  public @NotNull Collection<VcsRef> getRefs() {
    return new AbstractCollection<>() {
      private final Supplier<Collection<VcsRef>> myLoadedRefs =
        Suppliers.memoize(() -> CompressedRefs.this.stream().collect(Collectors.toList()));

      @Override
      public @NotNull Iterator<VcsRef> iterator() {
        return myLoadedRefs.get().iterator();
      }

      @Override
      public int size() {
        return myLoadedRefs.get().size();
      }
    };
  }

  public @NotNull Collection<Integer> getCommits() {
    Set<Integer> result = new HashSet<>();
    myBranches.keySet().intStream().forEach(result::add);
    myTags.keySet().intStream().forEach(result::add);
    return result;
  }
}
