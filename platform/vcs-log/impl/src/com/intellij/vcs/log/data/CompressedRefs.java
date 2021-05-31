// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.vcs.log.VcsRef;
import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CompressedRefs {
  @NotNull private final VcsLogStorage myStorage;

  // maps each commit id to the list of tag ids on this commit
  @NotNull private final Int2ObjectMap<IntArrayList> myTags = new Int2ObjectOpenHashMap<>();
  // maps each commit id to the list of branches on this commit
  @NotNull private final Int2ObjectMap<List<VcsRef>> myBranches = new Int2ObjectOpenHashMap<>();

  public CompressedRefs(@NotNull Set<VcsRef> refs, @NotNull VcsLogStorage storage) {
    myStorage = storage;

    Ref<VirtualFile> root = new Ref<>();

    refs.forEach(ref -> {
      assert root.get() == null || root.get().equals(ref.getRoot()) : "All references are supposed to be from the single root";
      root.set(ref.getRoot());

      if (ref.getType().isBranch()) {
        putRef(myBranches, ref, myStorage);
      }
      else {
        putRefIndex(myTags, ref, myStorage);
      }
    });
    //noinspection SSBasedInspection
    myTags.values().forEach(list -> {
      list.trim();
    });
    myStorage.flush();
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
      tags.forEach(value -> {
        result.add(myStorage.getVcsRef(value));
      });
    }
    return result;
  }

  @NotNull
  public Stream<VcsRef> streamBranches() {
    return myBranches.values().stream().flatMap(Collection::stream);
  }

  @NotNull
  private Stream<VcsRef> streamTags() {
    return myTags.values().stream().flatMapToInt(IntCollection::intStream).mapToObj(myStorage::getVcsRef);
  }

  @NotNull
  public Stream<VcsRef> stream() {
    return Stream.concat(streamBranches(), streamTags());
  }

  @NotNull
  public Collection<VcsRef> getRefs() {
    return new AbstractCollection<>() {
      private final Supplier<Collection<VcsRef>> myLoadedRefs =
        Suppliers.memoize(() -> CompressedRefs.this.stream().collect(Collectors.toList()));

      @NotNull
      @Override
      public Iterator<VcsRef> iterator() {
        return myLoadedRefs.get().iterator();
      }

      @Override
      public int size() {
        return myLoadedRefs.get().size();
      }
    };
  }

  @NotNull
  public Collection<Integer> getCommits() {
    Set<Integer> result = new HashSet<>();
    myBranches.keySet().intStream().forEach(result::add);
    myTags.keySet().intStream().forEach(result::add);
    return result;
  }

  private static void putRef(@NotNull Int2ObjectMap<List<VcsRef>> map, @NotNull VcsRef ref, @NotNull VcsLogStorage storage) {
    int index = storage.getCommitIndex(ref.getCommitHash(), ref.getRoot());
    List<VcsRef> list = map.get(index);
    if (list == null) map.put(index, list = new SmartList<>());
    list.add(ref);
  }

  private static void putRefIndex(@NotNull Int2ObjectMap<IntArrayList> map, @NotNull VcsRef ref, @NotNull VcsLogStorage storage) {
    int index = storage.getCommitIndex(ref.getCommitHash(), ref.getRoot());
    //noinspection SSBasedInspection
    IntArrayList list = map.get(index);
    if (list == null) map.put(index, list = new IntArrayList());
    list.add(storage.getRefIndex(ref));
  }
}
