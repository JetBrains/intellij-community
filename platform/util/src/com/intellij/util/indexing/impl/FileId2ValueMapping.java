// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SmartList;
import com.intellij.util.indexing.ValueContainer;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntObjectHashMap;

import java.util.List;

final class FileId2ValueMapping<Value> {
  private static final Logger LOG = Logger.getInstance(FileId2ValueMapping.class);

  private final TIntObjectHashMap<Value> id2ValueMap;
  private final ValueContainerImpl<Value> valueContainer;
  private boolean myOnePerFileValidationEnabled = true;

  FileId2ValueMapping(ValueContainerImpl<Value> _valueContainer) {
    id2ValueMap = new TIntObjectHashMap<>();
    valueContainer = _valueContainer;

    TIntArrayList removedFileIdList = null;
    List<Value> removedValueList = null;

    for (final ValueContainer.ValueIterator<Value> valueIterator = _valueContainer.getValueIterator(); valueIterator.hasNext();) {
      final Value value = valueIterator.next();

      for (final ValueContainer.IntIterator intIterator = valueIterator.getInputIdsIterator(); intIterator.hasNext();) {
        int id = intIterator.next();
        Value previousValue = id2ValueMap.put(id, value);
        if (previousValue != null) {  // delay removal of duplicated id -> value mapping since it will affect valueIterator we are using
          if (removedFileIdList == null) {
            removedFileIdList = new TIntArrayList();
            removedValueList = new SmartList<>();
          }
          removedFileIdList.add(id);
          removedValueList.add(previousValue);
        }
      }
    }

    if (removedFileIdList != null) {
      for(int i = 0, size = removedFileIdList.size(); i < size; ++i) {
        valueContainer.removeValue(removedFileIdList.get(i), removedValueList.get(i));
      }
    }
  }

  void associateFileIdToValue(int fileId, Value value) {
    Value previousValue = id2ValueMap.put(fileId, value);
    if (previousValue != null) {
      valueContainer.removeValue(fileId, previousValue);
    }
  }

  boolean removeFileId(int inputId) {
    Value mapped = id2ValueMap.remove(inputId);
    if (mapped != null) {
      valueContainer.removeValue(inputId, mapped);
    }
    if (IndexDebugProperties.EXTRA_SANITY_CHECKS && myOnePerFileValidationEnabled) {
      for (final InvertedIndexValueIterator<Value> valueIterator = valueContainer.getValueIterator(); valueIterator.hasNext();) {
        valueIterator.next();
        LOG.assertTrue(!valueIterator.getValueAssociationPredicate().test(inputId));
      }
    }
    return mapped != null;
  }

  public void disableOneValuePerFileValidation() {
    myOnePerFileValidationEnabled = false;
  }
}
