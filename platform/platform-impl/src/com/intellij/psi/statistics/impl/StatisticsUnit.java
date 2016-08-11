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

import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashMap;

import java.io.*;
import java.util.*;

class StatisticsUnit {
  private static final int FORMAT_VERSION_NUMBER = 5;

  private final int myNumber;

  private final THashMap<String, LinkedList<String>> myDataMap = new THashMap<>();

  public StatisticsUnit(int number) {
    myNumber = number;
  }

  public int getRecency(String key1, String key2) {
    final List<String> list = myDataMap.get(key1);
    if (list == null) return Integer.MAX_VALUE;

    int i = list.indexOf(key2);
    return i >= 0 ? i : Integer.MAX_VALUE;
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
  }

  public String[] getKeys2(final String key1){
    final List<String> list = myDataMap.get(key1);
    if (list == null) return ArrayUtil.EMPTY_STRING_ARRAY;

    return ArrayUtil.toStringArray(new LinkedHashSet<>(list));
  }

  public int getNumber() {
    return myNumber;
  }

  public void write(OutputStream out) throws IOException{
    final DataOutputStream dataOut = new DataOutputStream(out);
    dataOut.writeInt(FORMAT_VERSION_NUMBER);

    dataOut.writeInt(myDataMap.size());
    for (final String context : myDataMap.keySet()) {
      final List<String> list = myDataMap.get(context);
      if (list != null && !list.isEmpty()) {
        dataOut.writeUTF(context);
        dataOut.writeInt(list.size());
        for (final String data : list) {
          dataOut.writeUTF(data);
        }
      }
    }
  }

  public void read(InputStream in) throws IOException, WrongFormatException {
    DataInputStream dataIn = new DataInputStream(in);
    int formatVersion = dataIn.readInt();
    if (formatVersion != FORMAT_VERSION_NUMBER){
      throw new WrongFormatException();
    }

    myDataMap.clear();
    int size = dataIn.readInt();
    for(int i = 0; i < size; i++){
      String context = dataIn.readUTF();
      int len = dataIn.readInt();
      LinkedList<String> list = new LinkedList<>();
      for (int j = 0; j < len; j++) {
        list.add(dataIn.readUTF());
      }
      myDataMap.put(context, list);
    }
  }

}