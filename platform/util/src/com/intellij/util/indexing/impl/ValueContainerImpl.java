// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.SingletonIterator;
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
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.function.IntPredicate;

import static com.intellij.util.SystemProperties.getBooleanProperty;

@ApiStatus.Internal
public class ValueContainerImpl<Value> extends UpdatableValueContainer<Value> implements Cloneable {
  static final Logger LOG = Logger.getInstance(ValueContainerImpl.class);

  private static final boolean DO_EXPENSIVE_CHECKS = (IndexDebugProperties.IS_UNIT_TEST_MODE ||
                                                      IndexDebugProperties.EXTRA_SANITY_CHECKS) && !IndexDebugProperties.IS_IN_STRESS_TESTS;
  private static final boolean USE_SYNCHRONIZED_VALUE_CONTAINER = getBooleanProperty("idea.use.synchronized.value.container", false);

  /**
   * There is no volatile as we modify under write lock and read under read lock
   * <p>
   * Storage is optimized for 0 or 1 (value, inputId*) entry, which is the most often (80%) case:
   * <pre>
   * 0 entries:  (myInputIdMapping, myInputIdMappingValue) = (null, null)
   * 1 entry:    (myInputIdMapping, myInputIdMappingValue) = (value, inputId*)
   * >1 entries: (myInputIdMapping, myInputIdMappingValue) = (ValueToInputMap[ Value -> inputId*], null)
   * </pre>
   * <p>
   * inputId* (=set of inputId, also mentioned as FileSet in code) is also stored to optimize for the most
   * frequent case of 0-1 inputIds: it is either null (empty set), Integer (1-element set) or {@link ChangeBufferingList}
   * for a >1 inputIds.
   * See e.g. {@link #addValue(int, Object)} for decoding code.
   *
   * @see ValueToInputMap
   */
  private Object myInputIdMapping;
  private Object myInputIdMappingValue;

  //fields below are for expensiveSelfChecks, they are null if expensiveSelfChecks=false
  private Int2ObjectMap<Object> myPresentInputIds;
  private List<UpdateOp> myUpdateOps;

  public static <Value> ValueContainerImpl<Value> createNewValueContainer() {
    return USE_SYNCHRONIZED_VALUE_CONTAINER
           ? new SynchronizedValueContainerImpl<>()
           : new ValueContainerImpl<>();
  }

  public static <Value> ValueContainerImpl<Value> createNewValueContainer(boolean doExpensiveChecks) {
    return USE_SYNCHRONIZED_VALUE_CONTAINER
           ? new SynchronizedValueContainerImpl<>(doExpensiveChecks)
           : new ValueContainerImpl<>(doExpensiveChecks);
  }

  ValueContainerImpl() {
    this(DO_EXPENSIVE_CHECKS);
  }

  ValueContainerImpl(boolean doExpensiveChecks) {
    myPresentInputIds = doExpensiveChecks ? new Int2ObjectOpenHashMap<>() : null;
    myUpdateOps = doExpensiveChecks ? new SmartList<>() : null;
  }

  private static final class UpdateOp {
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
        LOG.error("Can't remove value '" +
                  normalizedValue +
                  "'; input id " +
                  inputId +
                  " is not present for the specified value in:\n" +
                  getDebugMessage());
      }
    }
  }

  private @Nullable ValueToInputMap<Value> asMapping() {
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
    return myInputIdMapping != null ? myInputIdMapping instanceof ValueToInputMap ? ((ValueToInputMap<?>)myInputIdMapping).size() : 1 : 0;
  }

  @Override
  public boolean removeAssociatedValue(int inputId) {
    for (InvertedIndexValueIterator<Value> valueIterator = getValueIterator(); valueIterator.hasNext(); ) {
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

  //TODO RC: replace with NotNullizer
  static @NotNull <Value> Value wrapValue(Value value) {
    //noinspection unchecked
    return value == null ? (Value)ObjectUtils.NULL : value;
  }

  static <Value> Value unwrap(Value value) {
    return value == ObjectUtils.NULL ? null : value;
  }

  @Override
  public @NotNull InvertedIndexValueIterator<Value> getValueIterator() {
    if (myInputIdMapping == null) {
      //noinspection unchecked
      return (InvertedIndexValueIterator<Value>)EmptyValueIterator.INSTANCE;
    }

    Map<Value, Object> mapping = asMapping();
    Iterator<Map.Entry<Value, Object>> iterator;
    if (mapping == null) {
      Map.Entry<Value, Object> entry = new SimpleImmutableEntry<>(
        wrapValue(asValue()),
        myInputIdMappingValue
      );
      iterator = new SingletonIterator<>(entry);
    }
    else {
      iterator = mapping.entrySet().iterator();
    }
    return new InvertedIndexValueIterator<Value>() {
      private Value currentValue;
      private Object currentFileSet;

      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public Value next() {
        Map.Entry<Value, Object> entry = iterator.next();
        currentValue = entry.getKey();
        Value next = currentValue;
        currentFileSet = entry.getValue();
        return unwrap(next);
      }

      @Override
      public @NotNull IntIterator getInputIdsIterator() {
        return getIntIteratorOutOfFileSetObject(getFileSetObject());
      }

      @Override
      public @NotNull IntPredicate getValueAssociationPredicate() {
        return getPredicateOutOfFileSetObject(getFileSetObject());
      }

      @Override
      public Object getFileSetObject() {
        if (currentValue == null) throw new IllegalStateException();
        return currentFileSet;
      }
    };
  }

  private static final class EmptyValueIterator<Value> implements InvertedIndexValueIterator<Value> {
    private static final EmptyValueIterator<Object> INSTANCE = new EmptyValueIterator<>();

    @Override
    public @NotNull ValueContainer.IntIterator getInputIdsIterator() {
      throw new IllegalStateException();
    }

    @Override
    public @NotNull IntPredicate getValueAssociationPredicate() {
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

  private static @NotNull
  ValueContainer.IntIterator getIntIteratorOutOfFileSetObject(@Nullable Object input) {
    if (input == null) return EMPTY_ITERATOR;
    if (input instanceof Integer) {
      return new SingleValueIterator(((Integer)input).intValue());
    }
    return ((ChangeBufferingList)input).intIterator();
  }

  private Object getFileSetObject(Value value) {
    if (myInputIdMapping == null) return null;

    if (value == null) {
      if (myInputIdMapping == ObjectUtils.NULL) {
        return myInputIdMappingValue;
      }

      Map<Value, Object> mapping = asMapping();
      return mapping == null ? null : mapping.get(ObjectUtils.NULL);
    }
    else {
      if (myInputIdMapping == value || myInputIdMapping.equals(value)) {
        return myInputIdMappingValue;
      }

      Map<Value, Object> mapping = asMapping();
      return mapping == null ? null : mapping.get(value);
    }
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

  private @Nullable ChangeBufferingList ensureFileSetCapacityForValue(Value value, int count) {
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
  public void saveTo(@NotNull DataOutput out,
                     @NotNull DataExternalizer<? super Value> externalizer) throws IOException {
    int size = size();
    DataInputOutputUtil.writeINT(out, size);

    if (size == 0) {
      return;
    }


    if (asMapping() == null) {
      //single entry: skip creating iterator for a most frequent case
      Value value = unwrap(asValue());
      externalizer.save(out, value);
      storeFileSet(out, myInputIdMappingValue);
      return;
    }

    for (final InvertedIndexValueIterator<Value> valueIterator = getValueIterator(); valueIterator.hasNext(); ) {
      final Value value = valueIterator.next();
      externalizer.save(out, value);

      final Object fileSetObject = valueIterator.getFileSetObject();
      storeFileSet(out, fileSetObject);
    }
  }

  public static void storeFileSet(@NotNull DataOutput out,
                                  @NotNull Object fileSetObject) throws IOException {
    // format is either <single id> (positive int value)
    //               or <-ids count> <id_1> <id_2-id_1> <id_3-id_2> ... (i.e. diff-encoded ids)
    if (fileSetObject instanceof Integer) {
      final int singleFileId = (Integer)fileSetObject;
      checkFileIdSanity(singleFileId);
      DataInputOutputUtil.writeINT(out, singleFileId); // most common 90% case during index building
    }
    else {
      // serialize positive file ids with delta encoding
      final ChangeBufferingList originalInput = (ChangeBufferingList)fileSetObject;
      final IntIdsIterator intIterator = originalInput.sortedIntIterator();
      if (IndexDebugProperties.DEBUG) LOG.assertTrue(intIterator.hasAscendingOrder());

      if (intIterator.size() == 1) {
        final int singleFileId = intIterator.next();
        checkFileIdSanity(singleFileId);
        DataInputOutputUtil.writeINT(out, singleFileId);
      }
      else {
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
            doCompact |= removeValue(mapping, inputId);
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
            int singleId = idCountOrSingleValue;
            @NotNull Object inputIds = remapping.remap(singleId);

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
            int idsCount = -idCountOrSingleValue;
            ChangeBufferingList changeBufferingList = ensureFileSetCapacityForValue(value, idsCount);
            int prev = 0;

            for (int i = 0; i < idsCount; i++) {
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

  /** @return true if something was actually removed */
  private boolean removeValue(@Nullable FileId2ValueMapping<Value> mapping,
                              int inputId) {
    if (mapping != null) {
      return mapping.removeFileId(inputId);
    }
    else {
      return removeAssociatedValue(inputId);
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

  private void associateValueOptimizely(FileId2ValueMapping<Value> mapping,
                                        Value value,
                                        ChangeBufferingList changeBufferingList,
                                        int inputId) {
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

  /**
   * Dedicated class to distinguish a difference between user-value with Object2ObjectOpenHashMap type and internal value container.
   */
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
