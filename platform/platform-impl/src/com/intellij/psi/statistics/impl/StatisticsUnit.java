/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.statistics.impl;

import com.intellij.openapi.util.io.DataInputOutputUtilRt;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ObjectIntHashMap;
import com.intellij.util.io.IOUtil;
import gnu.trove.THashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

class StatisticsUnit {
  private static final int FORMAT_VERSION_NUMBER = 6;

  private final int myNumber;

  private final Map<String, LinkedList<String>> myDataMap = new THashMap<>();
  private final TObjectIntHashMap<String> myContextMaxStamps = new TObjectIntHashMap<>();
  private final Map<String, TObjectIntHashMap<String>> myValueStamps = new THashMap<>();

  StatisticsUnit(int number) {
    myNumber = number;
  }

  int getRecency(String context, String value) {
    TObjectIntHashMap<String> perContext = myValueStamps.get(context);
    int stamp = perContext == null ? - 1 : perContext.get(value);
    if (stamp < 0) return Integer.MAX_VALUE;

    int diff = myContextMaxStamps.get(context) - stamp;
    return diff >= StatisticsManager.RECENCY_OBLIVION_THRESHOLD ? Integer.MAX_VALUE : diff;
  }

  public int getData(String key1, String key2) {
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
    int stamp = myContextMaxStamps.get(context) + 1;
    myContextMaxStamps.put(context, stamp);
    getValueStamps(context).put(value, stamp);

    if (stamp > StatisticsManager.RECENCY_OBLIVION_THRESHOLD * 2) {
      trimAncientRecencyEntries(context, StatisticsManager.RECENCY_OBLIVION_THRESHOLD);
    }
  }

  @NotNull
  private TObjectIntHashMap<String> getValueStamps(String context) {
    TObjectIntHashMap<String> valueStamps = myValueStamps.get(context);
    if (valueStamps == null) {
      myValueStamps.put(context, valueStamps = new ObjectIntHashMap<>());
    }
    return valueStamps;
  }

  private void trimAncientRecencyEntries(String context, int limit) {
    TObjectIntHashMap<String> newStamps = new ObjectIntHashMap<>();
    for (Object o : getValueStamps(context).keys()) {
      int recency = getRecency(context, (String)o);
      if (recency != Integer.MAX_VALUE) {
        newStamps.put((String)o, limit - recency);
      }
    }
    myValueStamps.put(context, newStamps);
    myContextMaxStamps.put(context, limit);
  }

  String[] getKeys2(final String key1){
    final List<String> list = myDataMap.get(key1);
    if (list == null) return ArrayUtil.EMPTY_STRING_ARRAY;

    return ArrayUtil.toStringArray(new LinkedHashSet<>(list));
  }

  int getNumber() {
    return myNumber;
  }

  void write(OutputStream out) throws IOException{
    final DataOutputStream dataOut = new DataOutputStream(out);
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

    DataInputStream dataIn = new DataInputStream(in);
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
      ObjectIntHashMap<String> map = new ObjectIntHashMap<>();
      myValueStamps.put(IOUtil.readUTF(dataIn), map);
      readStringIntMap(dataIn, map);
      return null;
    });
  }

  private static void writeStringIntMap(DataOutputStream dataOut, TObjectIntHashMap<String> map) throws IOException {
    DataInputOutputUtilRt.writeINT(dataOut, map.size());
    for (Object context : map.keys()) {
      IOUtil.writeUTF(dataOut, (String)context);
      DataInputOutputUtilRt.writeINT(dataOut, map.get((String)context));
    }
  }

  private static void readStringIntMap(DataInputStream dataIn, TObjectIntHashMap<String> map) throws IOException {
    int count = DataInputOutputUtilRt.readINT(dataIn);
    for (int i = 0; i < count; i++) {
      map.put(IOUtil.readUTF(dataIn), DataInputOutputUtilRt.readINT(dataIn));
    }
  }

}