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
package com.intellij.vcs.log.data;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.util.TroveUtil;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CompressedRefs {
  @NotNull private final VcsLogStorage myStorage;

  // maps each commit id to the list of tag ids on this commit
  @NotNull private final TIntObjectHashMap<TIntArrayList> myTags = new TIntObjectHashMap<>();
  // maps each commit id to the list of branches on this commit
  @NotNull private final TIntObjectHashMap<List<VcsRef>> myBranches = new TIntObjectHashMap<>();

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
    myTags.forEachValue(list -> {
      list.trimToSize();
      return true;
    });
    myStorage.flush();
  }

  @NotNull
  SmartList<VcsRef> refsToCommit(int index) {
    SmartList<VcsRef> result = new SmartList<>();
    if (myBranches.containsKey(index)) result.addAll(myBranches.get(index));
    TIntArrayList tags = myTags.get(index);
    if (tags != null) {
      tags.forEach(value -> {
        result.add(myStorage.getVcsRef(value));
        return true;
      });
    }
    return result;
  }

  @NotNull
  Stream<VcsRef> streamBranches() {
    return TroveUtil.streamValues(myBranches).flatMap(Collection::stream);
  }

  @NotNull
  private Stream<VcsRef> streamTags() {
    return TroveUtil.streamValues(myTags).flatMapToInt(TroveUtil::stream).mapToObj(myStorage::getVcsRef);
  }

  @NotNull
  public Stream<VcsRef> stream() {
    return Stream.concat(streamBranches(), streamTags());
  }

  @NotNull
  public Collection<VcsRef> getRefs() {
    return new AbstractCollection<VcsRef>() {
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
    Set<Integer> result = ContainerUtil.newHashSet();
    TroveUtil.streamKeys(myBranches).forEach(result::add);
    TroveUtil.streamKeys(myTags).forEach(result::add);
    return result;
  }

  private static void putRef(@NotNull TIntObjectHashMap<List<VcsRef>> map, @NotNull VcsRef ref, @NotNull VcsLogStorage storage) {
    int index = storage.getCommitIndex(ref.getCommitHash(), ref.getRoot());
    List<VcsRef> list = map.get(index);
    if (list == null) map.put(index, list = new SmartList<>());
    list.add(ref);
  }

  private static void putRefIndex(@NotNull TIntObjectHashMap<TIntArrayList> map, @NotNull VcsRef ref, @NotNull VcsLogStorage storage) {
    int index = storage.getCommitIndex(ref.getCommitHash(), ref.getRoot());
    TIntArrayList list = map.get(index);
    if (list == null) map.put(index, list = new TIntArrayList());
    list.add(storage.getRefIndex(ref));
  }
}
