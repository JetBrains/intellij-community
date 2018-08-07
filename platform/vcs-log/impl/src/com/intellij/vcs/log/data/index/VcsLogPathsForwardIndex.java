// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data.index;

import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.IndexExtension;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.indexing.impl.KeyValueUpdateProcessor;
import com.intellij.util.indexing.impl.MapBasedForwardIndex;
import com.intellij.util.indexing.impl.RemovedKeyProcessor;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class VcsLogPathsForwardIndex
  extends MapBasedForwardIndex<Integer, List<VcsLogPathsIndex.ChangeData>, List<Collection<Integer>>> {

  protected VcsLogPathsForwardIndex(@NotNull IndexExtension<Integer, List<VcsLogPathsIndex.ChangeData>, VcsFullCommitDetails> extension)
    throws IOException {
    super(extension);
  }

  @Override
  protected InputDataDiffBuilder<Integer, List<VcsLogPathsIndex.ChangeData>> getDiffBuilder(int inputId,
                                                                                            @Nullable List<Collection<Integer>> oldData) {
    return new VcsLogPathsDiffBuilder(inputId, oldData);
  }

  @Override
  protected List<Collection<Integer>> convertToMapValueType(int inputId, @NotNull Map<Integer, List<VcsLogPathsIndex.ChangeData>> map) {
    return convertToMapValueType(map);
  }

  @NotNull
  static List<Collection<Integer>> convertToMapValueType(@NotNull Map<Integer, List<VcsLogPathsIndex.ChangeData>> map) {
    SmartList<Collection<Integer>> result = new SmartList<>();

    for (Map.Entry<Integer, List<VcsLogPathsIndex.ChangeData>> entry : map.entrySet()) {
      Integer fileId = entry.getKey();
      List<VcsLogPathsIndex.ChangeData> changes = entry.getValue();
      while (result.size() < changes.size()) {
        result.add(ContainerUtil.newHashSet());
      }
      for (int i = 0; i < changes.size(); i++) {
        if (changes.get(i) != VcsLogPathsIndex.ChangeData.NOT_CHANGED) {
          result.get(i).add(fileId);
        }
      }
    }

    return result;
  }

  public static class IntCollectionListExternalizer implements DataExternalizer<List<Collection<Integer>>> {

    public void save(@NotNull DataOutput out, @NotNull List<Collection<Integer>> value) throws IOException {
      DataInputOutputUtil.writeINT(out, value.size());
      for (Collection<Integer> collection : value) {
        DataInputOutputUtil.writeINT(out, collection.size());
        for (int i : collection) {
          DataInputOutputUtil.writeINT(out, i);
        }
      }
    }

    @NotNull
    public List<Collection<Integer>> read(@NotNull DataInput in) throws IOException {
      SmartList<Collection<Integer>> result = new SmartList<>();
      int listSize = DataInputOutputUtil.readINT(in);
      for (int i = 0; i < listSize; i++) {
        Set<Integer> collection = ContainerUtil.newHashSet();
        result.add(collection);

        int collectionSize = DataInputOutputUtil.readINT(in);
        for (int j = 0; j < collectionSize; j++) {
          collection.add(DataInputOutputUtil.readINT(in));
        }
      }
      return result;
    }
  }

  static class VcsLogPathsDiffBuilder extends InputDataDiffBuilder<Integer, List<VcsLogPathsIndex.ChangeData>> {
    @Nullable private final List<Collection<Integer>> myOldData;

    public VcsLogPathsDiffBuilder(int id, @Nullable List<Collection<Integer>> oldData) {
      super(id);
      myOldData = oldData;
    }

    @Override
    public boolean differentiate(@NotNull Map<Integer, List<VcsLogPathsIndex.ChangeData>> newData,
                                 @NotNull KeyValueUpdateProcessor<Integer, List<VcsLogPathsIndex.ChangeData>> addProcessor,
                                 @NotNull KeyValueUpdateProcessor<Integer, List<VcsLogPathsIndex.ChangeData>> updateProcessor,
                                 @NotNull RemovedKeyProcessor<Integer> removeProcessor) throws StorageException {

      if (myOldData == null) {
        return processNewFiles(newData, addProcessor);
      }

      // we are only here if reindexWithRenames happens (no new data, unless copies are tracked)
      // or if myOldData is empty
      Map<Integer, List<VcsLogPathsIndex.ChangeData>> newFiles = ContainerUtil.filter(newData, it -> !isFileInOldData(it));
      return processNewFiles(newFiles, addProcessor);
    }

    public boolean processNewFiles(@NotNull Map<Integer, List<VcsLogPathsIndex.ChangeData>> newData,
                                   @NotNull KeyValueUpdateProcessor<Integer, List<VcsLogPathsIndex.ChangeData>> addProcessor)
      throws StorageException {
      for (Map.Entry<Integer, List<VcsLogPathsIndex.ChangeData>> entry : newData.entrySet()) {
        addProcessor.process(entry.getKey(), entry.getValue(), myInputId);
      }
      return !newData.isEmpty();
    }

    private boolean isFileInOldData(@NotNull Integer fileId) {
      if (myOldData == null) return false;

      return ContainerUtil.find(myOldData, files -> files.contains(fileId)) != null;
    }
  }
}
