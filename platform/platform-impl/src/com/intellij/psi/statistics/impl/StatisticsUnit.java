// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.statistics.impl;

import com.intellij.openapi.util.io.DataInputOutputUtilRt;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.util.io.IOUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;

final class StatisticsUnit {
  private static final int FORMAT_VERSION_NUMBER = 6;

  private final int myNumber;

  private final Map<String, LinkedList<String>> myDataMap = new HashMap<>();
  private final Object2IntMap<String> myContextMaxStamps = new Object2IntOpenHashMap<>();
  private final Map<String, Object2IntMap<String>> myValueStamps = new HashMap<>();

  StatisticsUnit(int number) {
    myNumber = number;
  }

  int getRecency(String context, String value) {
    Object2IntMap<String> perContext = myValueStamps.get(context);
    int stamp = perContext == null ? - 1 : perContext.getInt(value);
    if (stamp < 0) return Integer.MAX_VALUE;

    int diff = myContextMaxStamps.getInt(context) - stamp;
    return diff >= StatisticsManager.RECENCY_OBLIVION_THRESHOLD ? Integer.MAX_VALUE : diff;
  }

  public int getData(@NotNull String key1, @NotNull String key2) {
    final List<String> list = myDataMap.get(key1);
    if (list == null) return 0;

    int result = 0;
    for (final String s : list) {
      if (s.equals(key2)) result++;
    }
    return result;
  }

  public void incData(String key1, String key2) {
    LinkedList<String> list = myDataMap.get(key1);
    if (list == null) {
      myDataMap.put(key1, list = new LinkedList<>());
    }
    list.addFirst(key2);
    if (list.size() > StatisticsManager.OBLIVION_THRESHOLD) {
      list.removeLast();
    }

    advanceRecencyStamps(key1, key2);
  }

  private void advanceRecencyStamps(String context, String value) {
    int stamp = myContextMaxStamps.getInt(context) + 1;
    myContextMaxStamps.put(context, stamp);
    getValueStamps(context).put(value, stamp);

    if (stamp > StatisticsManager.RECENCY_OBLIVION_THRESHOLD * 2) {
      trimAncientRecencyEntries(context, StatisticsManager.RECENCY_OBLIVION_THRESHOLD);
    }
  }

  private @NotNull Object2IntMap<String> getValueStamps(String context) {
    return myValueStamps.computeIfAbsent(context, __ -> {
      Object2IntMap<String> result = new Object2IntOpenHashMap<>();
      result.defaultReturnValue(-1);
      return result;
    });
  }

  private void trimAncientRecencyEntries(String context, int limit) {
    Object2IntMap<String> newStamps = new Object2IntOpenHashMap<>();
    newStamps.defaultReturnValue(-1);
    for (String o : getValueStamps(context).keySet()) {
      int recency = getRecency(context, o);
      if (recency != Integer.MAX_VALUE) {
        newStamps.put(o, limit - recency);
      }
    }
    myValueStamps.put(context, newStamps);
    myContextMaxStamps.put(context, limit);
  }

  @NotNull
  Collection<String> getKeys2(@NotNull String key1) {
    List<String> list = myDataMap.get(key1);
    return list == null ? Collections.emptyList() : new LinkedHashSet<>(list);
  }

  int getNumber() {
    return myNumber;
  }

  void write(OutputStream out) throws IOException{
    DataOutput dataOut = new DataOutputStream(out);
    dataOut.writeInt(FORMAT_VERSION_NUMBER);

    DataInputOutputUtilRt.writeSeq(dataOut, myDataMap.entrySet(), entry -> {
      IOUtil.writeUTF(dataOut, entry.getKey());
      DataInputOutputUtilRt.writeSeq(dataOut, entry.getValue(), data -> IOUtil.writeUTF(dataOut, data));
    });

    writeStringIntMap(dataOut, myContextMaxStamps);

    DataInputOutputUtilRt.writeSeq(dataOut, myValueStamps.entrySet(), entry -> {
      IOUtil.writeUTF(dataOut, entry.getKey());
      writeStringIntMap(dataOut, entry.getValue());
    });
  }

  void read(InputStream in) throws IOException, WrongFormatException {
    myDataMap.clear();
    myContextMaxStamps.clear();
    myValueStamps.clear();

    DataInput dataIn = new DataInputStream(in);
    int formatVersion = dataIn.readInt();
    if (formatVersion != FORMAT_VERSION_NUMBER){
      throw new WrongFormatException();
    }

    DataInputOutputUtilRt.readSeq(dataIn, () -> {
      myDataMap.put(IOUtil.readUTF(dataIn),
                    new LinkedList<>(DataInputOutputUtilRt.readSeq(dataIn, () -> IOUtil.readUTF(dataIn))));
      return null;
    });

    readStringIntMap(dataIn, myContextMaxStamps);

    DataInputOutputUtilRt.readSeq(dataIn, () -> {
      Object2IntMap<String> map = new Object2IntOpenHashMap<>();
      map.defaultReturnValue(-1);
      myValueStamps.put(IOUtil.readUTF(dataIn), map);
      readStringIntMap(dataIn, map);
      return null;
    });
  }

  private static void writeStringIntMap(DataOutput dataOut, Object2IntMap<String> map) throws IOException {
    DataInputOutputUtilRt.writeINT(dataOut, map.size());
    for (String context : map.keySet()) {
      IOUtil.writeUTF(dataOut, context);
      DataInputOutputUtilRt.writeINT(dataOut, map.getInt(context));
    }
  }

  private static void readStringIntMap(DataInput dataIn, Object2IntMap<String> map) throws IOException {
    int count = DataInputOutputUtilRt.readINT(dataIn);
    for (int i = 0; i < count; i++) {
      map.put(IOUtil.readUTF(dataIn), DataInputOutputUtilRt.readINT(dataIn));
    }
  }
}