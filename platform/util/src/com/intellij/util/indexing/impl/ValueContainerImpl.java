// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.indexing.containers.ChangeBufferingList;
import com.intellij.util.indexing.containers.IntIdsIterator;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;
import java.util.function.IntPredicate;

@ApiStatus.Internal
public final class ValueContainerImpl<Value> extends UpdatableValueContainer<Value> implements Cloneable{
  static final Logger LOG = Logger.getInstance(ValueContainerImpl.class);
  private static final boolean DO_EXPENSIVE_CHECKS = (IndexDebugProperties.IS_UNIT_TEST_MODE ||
                                                     IndexDebugProperties.EXTRA_SANITY_CHECKS) && !IndexDebugProperties.IS_IN_STRESS_TESTS;

  // there is no volatile as we modify under write lock and read under read lock
  // Most often (80%) we store 0 or one mapping, then we store them in two fields: myInputIdMapping, myInputIdMappingValue
  // when there are several value mapped, myInputIdMapping is ValueToInputMap<Value, Data> (it's actually just THashMap), myInputIdMappingValue = null
  private Object myInputIdMapping;
  private Object myInputIdMappingValue;
  private Int2ObjectMap<Object> myPresentInputIds;
  private List<UpdateOp> myUpdateOps;

  public ValueContainerImpl() {
    this(DO_EXPENSIVE_CHECKS);
  }

  public ValueContainerImpl(boolean doExpensiveChecks) {
    myPresentInputIds = doExpensiveChecks ? new Int2ObjectOpenHashMap<>() : null;
    myUpdateOps = doExpensiveChecks ? new SmartList<>() : null;
  }

  private static class UpdateOp {
    private UpdateOp(Type type, int id, Object value) {
      myType = type;
      myInputId = id;
      myValue = value;
    }

    @Override
    public String toString() {
      return "(" + myType + ", " + myInputId + ", " + myValue + ")";
    }

    private enum Type {
      ADD,
      ADD_DIRECT,
      REMOVE
    }

    private final Type myType;
    private final int myInputId;
    private final Object myValue;
  }
  @Override
  public void addValue(int inputId, Value value) {
    //TODO RC: should we check inputId > 0 here? Storage format assumes positive ids, and it's better
    //         to check this (and fail) as early as possible
    Object fileSetObject = getFileSetObject(value);

    ensureInputIdIsntAssociatedWithAnotherValue(inputId, value, false);

    if (fileSetObject == null) {
      attachFileSetForNewValue(value, inputId);
    }
    else if (fileSetObject instanceof Integer) {
      int existingValue = ((Integer)fileSetObject).intValue();
      if (existingValue != inputId) {
        ChangeBufferingList list = new ChangeBufferingList();
        list.add(existingValue);
        list.add(inputId);
        resetFileSetForValue(value, list);
      }
    }
    else {
      ((ChangeBufferingList)fileSetObject).add(inputId);
    }
  }

  private void ensureInputIdIsntAssociatedWithAnotherValue(int inputId, Value value, boolean isDirect) {
    if (myPresentInputIds != null) {
      Object normalizedValue = wrapValue(value);
      Object previousValue = myPresentInputIds.put(inputId, normalizedValue);
      myUpdateOps.add(new UpdateOp(isDirect ? UpdateOp.Type.ADD_DIRECT : UpdateOp.Type.ADD, inputId, normalizedValue));
      if (previousValue != null && !previousValue.equals(normalizedValue)) {
        LOG.error("Can't add value '" + normalizedValue + "'; input id " + inputId + " is already present in:\n" + getDebugMessage());
      }
    }
  }
  private void ensureInputIdAssociatedWithValue(int inputId, Value value) {
    if (myPresentInputIds != null) {
      Object normalizedValue = wrapValue(value);
      Object previousValue = myPresentInputIds.remove(inputId);
      myUpdateOps.add(new UpdateOp(UpdateOp.Type.REMOVE, inputId, normalizedValue));
      if (previousValue != null && !previousValue.equals(normalizedValue)) {
        LOG.error("Can't remove value '" + normalizedValue + "'; input id " + inputId + " is not present for the specified value in:\n" + getDebugMessage());
      }
    }
  }

  @Nullable
  private ValueToInputMap<Value> asMapping() {
    //noinspection unchecked
    return myInputIdMapping instanceof ValueToInputMap ? (ValueToInputMap<Value>)myInputIdMapping : null;
  }

  private Value asValue() {
    //noinspection unchecked
    return (Value)myInputIdMapping;
  }

  private void resetFileSetForValue(Value value, @NotNull Object fileSet) {
    value = wrapValue(value);
    Map<Value, Object> map = asMapping();
    if (map == null) {
      myInputIdMappingValue = fileSet;
    }
    else {
      map.put(value, fileSet);
    }
  }

  @Override
  public int size() {
    return myInputIdMapping != null ? myInputIdMapping instanceof ValueToInputMap ? ((ValueToInputMap<?>)myInputIdMapping).size(): 1 : 0;
  }

  @Override
  public boolean removeAssociatedValue(int inputId) {
    for (InvertedIndexValueIterator<Value> valueIterator = getValueIterator(); valueIterator.hasNext();) {
      Value value = valueIterator.next();
      if (valueIterator.getValueAssociationPredicate().test(inputId)) {
        removeValue(inputId, valueIterator.getFileSetObject(), value);
        return true;
      }
    }
    return false;
  }

  @NotNull
  String getDebugMessage() {
    return "Actual value container = \n" + this +
           (myPresentInputIds == null ? "" : "\nExpected value container = " + myPresentInputIds) +
           (myUpdateOps == null ? "" : "\nUpdate operations = " + myUpdateOps);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    forEach((id, value) -> {
      sb.append(id).append(" <-> '").append(value).append("'\n");
      return true;
    });
    return sb.toString();
  }

  void removeValue(int inputId, Value value) {
    removeValue(inputId, getFileSetObject(value), value);
  }

  private void removeValue(int inputId, Object fileSet, Value value) {
    ensureInputIdAssociatedWithValue(inputId, value);

    if (fileSet == null) {
      return;
    }

    if (fileSet instanceof ChangeBufferingList) {
      ChangeBufferingList changesList = (ChangeBufferingList)fileSet;
      changesList.remove(inputId);
      if (!changesList.isEmpty()) {
        return;
      }
    }
    else if (fileSet instanceof Integer) {
      if (((Integer)fileSet).intValue() != inputId) {
        return;
      }
    }

    value = wrapValue(value);
    Map<Value, Object> mapping = asMapping();
    if (mapping == null) {
      myInputIdMapping = null;
      myInputIdMappingValue = null;
    }
    else {
      mapping.remove(value);
      if (mapping.size() == 1) {
        Map.Entry<Value, Object> entry = mapping.entrySet().iterator().next();
        myInputIdMapping = entry.getKey();
        myInputIdMappingValue = entry.getValue();
      }
    }
  }

  @NotNull
  static <Value> Value wrapValue(Value value) {
    //noinspection unchecked
    return value == null ? (Value)ObjectUtils.NULL : value;
  }

  static <Value> Value unwrap(Value value) {
    return value == ObjectUtils.NULL ? null : value;
  }

  @NotNull
  @Override
  public InvertedIndexValueIterator<Value> getValueIterator() {
    if (myInputIdMapping == null) {
      //noinspection unchecked
      return (InvertedIndexValueIterator<Value>)EmptyValueIterator.INSTANCE;
    }
    Map<Value, Object> mapping = ObjectUtils.notNull(asMapping(),
                                                     Collections.singletonMap(wrapValue(asValue()), myInputIdMappingValue));
    return new InvertedIndexValueIterator<Value>() {
      private Value current;
      private Object currentValue;
      private final Iterator<Map.Entry<Value, Object>> iterator = mapping.entrySet().iterator();

      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public Value next() {
        Map.Entry<Value, Object> entry = iterator.next();
        current = entry.getKey();
        Value next = current;
        currentValue = entry.getValue();
        return unwrap(next);
      }

      @NotNull
      @Override
      public IntIterator getInputIdsIterator() {
        return getIntIteratorOutOfFileSetObject(getFileSetObject());
      }

      @NotNull
      @Override
      public IntPredicate getValueAssociationPredicate() {
        return getPredicateOutOfFileSetObject(getFileSetObject());
      }

      @Override
      public Object getFileSetObject() {
        if (current == null) throw new IllegalStateException();
        return currentValue;
      }
    };
  }

  private static class EmptyValueIterator<Value> implements InvertedIndexValueIterator<Value> {
    private static final EmptyValueIterator<Object> INSTANCE = new EmptyValueIterator<>();

    @NotNull
    @Override
    public ValueContainer.IntIterator getInputIdsIterator() {
      throw new IllegalStateException();
    }

    @NotNull
    @Override
    public IntPredicate getValueAssociationPredicate() {
      throw new IllegalStateException();
    }

    @Override
    public Object getFileSetObject() {
      throw new IllegalStateException();
    }

    @Override
    public boolean hasNext() {
      return false;
    }

    @Override
    public Value next() {
      throw new NoSuchElementException();
    }

    @Override
    public void remove() {
      throw new IllegalStateException();
    }
  }

  private static @NotNull IntPredicate getPredicateOutOfFileSetObject(@Nullable Object input) {
    if (input == null) return __ -> false;

    if (input instanceof Integer) {
      final int singleId = (Integer)input;

      return id -> id == singleId;
    }
    return ((ChangeBufferingList)input).intPredicate();
  }

  @NotNull
  private static
  ValueContainer.IntIterator getIntIteratorOutOfFileSetObject(@Nullable Object input) {
    if (input == null) return EMPTY_ITERATOR;
    if (input instanceof Integer) {
      return new SingleValueIterator(((Integer)input).intValue());
    }
    return ((ChangeBufferingList)input).intIterator();
  }

  private Object getFileSetObject(Value value) {
    if (myInputIdMapping == null) return null;

    value = wrapValue(value);

    if (myInputIdMapping == value || // myNullValue is Object
        myInputIdMapping.equals(value)) {
      return myInputIdMappingValue;
    }

    Map<Value, Object> mapping = asMapping();
    return mapping == null ? null : mapping.get(value);
  }

  @Override
  public ValueContainerImpl<Value> clone() {
    try {
      //noinspection unchecked
      ValueContainerImpl<Value> clone = (ValueContainerImpl<Value>)super.clone();
      ValueToInputMap<Value> mapping = asMapping();
      if (mapping != null) {
        ValueToInputMap<Value> cloned = mapping.clone();
        cloned.forEach((key, val) -> {
          if (val instanceof ChangeBufferingList) {
            cloned.put(key, ((ChangeBufferingList)val).clone());
          }
        });

        clone.myInputIdMapping = cloned;
      }
      else if (myInputIdMappingValue instanceof ChangeBufferingList) {
        clone.myInputIdMappingValue = ((ChangeBufferingList)myInputIdMappingValue).clone();
      }
      clone.myPresentInputIds = myPresentInputIds != null
                                ? new Int2ObjectOpenHashMap<>(myPresentInputIds)
                                : null;
      clone.myUpdateOps = myUpdateOps != null
                          ? new SmartList<>(myUpdateOps)
                          : null;
      return clone;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  public static final IntIdsIterator EMPTY_ITERATOR = new IntIdsIterator() {
    @Override
    public boolean hasNext() {
      return false;
    }

    @Override
    public int next() {
      return 0;
    }

    @Override
    public int size() {
      return 0;
    }

    @Override
    public boolean hasAscendingOrder() {
      return true;
    }

    @Override
    public IntIdsIterator createCopyInInitialState() {
      return this;
    }
  };

  @Nullable
  private ChangeBufferingList ensureFileSetCapacityForValue(Value value, int count) {
    if (count <= 1) return null;
    Object fileSetObject = getFileSetObject(value);

    if (fileSetObject != null) {
      if (fileSetObject instanceof Integer) {
        ChangeBufferingList list = new ChangeBufferingList(count + 1);
        list.add(((Integer)fileSetObject).intValue());
        resetFileSetForValue(value, list);
        return list;
      }
      if (fileSetObject instanceof ChangeBufferingList) {
        ChangeBufferingList list = (ChangeBufferingList)fileSetObject;
        list.ensureCapacity(count);
        return list;
      }
      return null;
    }

    final ChangeBufferingList fileSet = new ChangeBufferingList(count);
    attachFileSetForNewValue(value, fileSet);
    return fileSet;
  }

  private void attachFileSetForNewValue(Value value, Object fileSet) {
    value = wrapValue(value);

    if (myInputIdMapping != null) {
      Map<Value, Object> mapping = asMapping();
      if (mapping == null) {
        Value oldMapping = asValue();
        myInputIdMapping = mapping = new ValueToInputMap<>(2);
        mapping.put(oldMapping, myInputIdMappingValue);
        myInputIdMappingValue = null;
      }
      mapping.put(value, fileSet);
    }
    else {
      myInputIdMapping = value;
      myInputIdMappingValue = fileSet;
    }
  }

  @Override
  public void saveTo(final @NotNull DataOutput out,
                     final @NotNull DataExternalizer<? super Value> externalizer) throws IOException {
    DataInputOutputUtil.writeINT(out, size());

    for (final InvertedIndexValueIterator<Value> valueIterator = getValueIterator(); valueIterator.hasNext();) {
      final Value value = valueIterator.next();
      externalizer.save(out, value);

      final Object fileSetObject = valueIterator.getFileSetObject();
      storeFileSet(out, fileSetObject);
    }
  }

  public static void storeFileSet(final @NotNull DataOutput out,
                                  final @NotNull Object fileSetObject) throws IOException {
    // format is either <single id> (positive int value)
    //               or <-ids count> <id_1> <id_2-id_1> <id_3-id_2> ... (i.e. diff-encoded ids)
    if (fileSetObject instanceof Integer) {
      final int singleFileId = (Integer)fileSetObject;
      checkFileIdSanity(singleFileId);
      DataInputOutputUtil.writeINT(out, singleFileId); // most common 90% case during index building
    } else {
      // serialize positive file ids with delta encoding
      final ChangeBufferingList originalInput = (ChangeBufferingList)fileSetObject;
      final IntIdsIterator intIterator = originalInput.sortedIntIterator();
      if (IndexDebugProperties.DEBUG) LOG.assertTrue(intIterator.hasAscendingOrder());

      if (intIterator.size() == 1) {
        final int singleFileId = intIterator.next();
        checkFileIdSanity(singleFileId);
        DataInputOutputUtil.writeINT(out, singleFileId);
      } else {
        DataInputOutputUtil.writeINT(out, -intIterator.size());

        int prev = 0;
        while (intIterator.hasNext()) {
          int fileId = intIterator.next();
          DataInputOutputUtil.writeINT(out, fileId - prev);
          prev = fileId;
        }
      }
    }
  }

  static final int NUMBER_OF_VALUES_THRESHOLD = 20;

  public void readFrom(@NotNull DataInputStream stream,
                       @NotNull DataExternalizer<? extends Value> externalizer,
                       @NotNull ValueContainerInputRemapping remapping) throws IOException {
    FileId2ValueMapping<Value> mapping = null;

    while (stream.available() > 0) {
      final int valueCount = DataInputOutputUtil.readINT(stream);
      if (valueCount < 0) {
        // ChangeTrackingValueContainer marked inputId as invalidated, see ChangeTrackingValueContainer.saveTo
        @NotNull Object inputIds = remapping.remap(-valueCount);

        if (mapping == null && size() > NUMBER_OF_VALUES_THRESHOLD) { // avoid O(NumberOfValues)
          mapping = new FileId2ValueMapping<>(this);
        }

        boolean doCompact = false;
        if (inputIds instanceof int[]) {
          for (int inputId : (int[])inputIds) {
            doCompact = removeValue(mapping, inputId);
          }
        }
        else {
          int inputId = (int)inputIds;
          doCompact = removeValue(mapping, inputId);
        }

        if (doCompact) setNeedsCompacting(true);
      }
      else {
        for (int valueIdx = 0; valueIdx < valueCount; valueIdx++) {
          final Value value = externalizer.read(stream);
          int idCountOrSingleValue = DataInputOutputUtil.readINT(stream);

          if (idCountOrSingleValue > 0) {
            @NotNull Object inputIds = remapping.remap(idCountOrSingleValue);

            if (inputIds instanceof int[]) {
              for (int inputId : (int[])inputIds) {
                associateValue(mapping, value, inputId);
              }
            }
            else {
              int inputId = (int)inputIds;
              associateValue(mapping, value, inputId);
            }
          }
          else {
            idCountOrSingleValue = -idCountOrSingleValue;
            ChangeBufferingList changeBufferingList = ensureFileSetCapacityForValue(value, idCountOrSingleValue);
            int prev = 0;

            for (int i = 0; i < idCountOrSingleValue; i++) {
              final int id = DataInputOutputUtil.readINT(stream);
              @NotNull Object inputIds = remapping.remap(prev + id);

              if (inputIds instanceof int[]) {
                for (int inputId : (int[])inputIds) {
                  associateValueOptimizely(mapping, value, changeBufferingList, inputId);
                }
              }
              else {
                int inputId = (int)inputIds;
                associateValueOptimizely(mapping, value, changeBufferingList, inputId);
              }

              prev += id;
            }
          }
        }
      }
    }
  }

  private boolean removeValue(FileId2ValueMapping<Value> mapping, int inputId) {
    if (mapping != null) {
      return mapping.removeFileId(inputId);
    }
    else {
      removeAssociatedValue(inputId);
      return true;
    }
  }

  private void associateValue(FileId2ValueMapping<Value> mapping, Value value, int inputId) {
    if (mapping != null) {
      mapping.associateFileIdToValue(inputId, value);
    }
    else {
      addValue(inputId, value);
    }
  }

  private void associateValueOptimizely(FileId2ValueMapping<Value> mapping, Value value, ChangeBufferingList changeBufferingList, int inputId) {
    if (changeBufferingList != null) {
      ensureInputIdIsntAssociatedWithAnotherValue(inputId, value, true);
      changeBufferingList.add(inputId);
      if (mapping != null) {
        mapping.associateFileIdToValueSkippingContainer(inputId, value);
      }
    }
    else {
      if (mapping != null) {
        mapping.associateFileIdToValue(inputId, value);
      }
      else {
        addValue(inputId, value);
      }
    }
  }

  private static void checkFileIdSanity(final int singleFileId) {
    if (singleFileId <= 0) {
      throw new IllegalStateException("fileId(=" + singleFileId + ") must be >0");
    }
  }

  private static final class SingleValueIterator implements IntIdsIterator {
    private final int myValue;
    private boolean myValueRead;

    private SingleValueIterator(int value) {
      myValue = value;
    }

    @Override
    public boolean hasNext() {
      return !myValueRead;
    }

    @Override
    public int next() {
      myValueRead = true;
      return myValue;
    }

    @Override
    public int size() {
      return 1;
    }

    @Override
    public boolean hasAscendingOrder() {
      return true;
    }

    @Override
    public IntIdsIterator createCopyInInitialState() {
      return new SingleValueIterator(myValue);
    }
  }

  // a class to distinguish a difference between user-value with Object2ObjectOpenHashMap type and internal value container
  private static final class ValueToInputMap<Value> extends Object2ObjectOpenHashMap<Value, Object> {
    ValueToInputMap(int size) {
      super(size);
    }

    @Override
    public ValueToInputMap<Value> clone() {
      return (ValueToInputMap<Value>)super.clone();
    }
  }
}
