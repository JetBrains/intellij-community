// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.igtest.performance.map_replaceable_by_enum_map;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.SortedMap;

public class MapReplaceableByEnumMap {

  enum MyEnum {
    A, B, C
  }

  public static void main(String[] args) {
    final Map<MyEnum, Object> myEnums = new java.util.EnumMap<MyEnum, Object>(MyEnum.class);
  }
}
