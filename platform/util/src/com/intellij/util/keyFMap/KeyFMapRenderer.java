// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.keyFMap;

import com.intellij.openapi.util.Key;

import java.util.AbstractMap;
import java.util.Map;

// Used in KeyFMap debugger renderer
final class KeyFMapRenderer {
  static Map.Entry[] childrenArray(KeyFMap map) {
    Key[] keys = map.getKeys();
    int length = map.size();
    Map.Entry[] res = new Map.Entry[length];
    for (int i = 0; i < length; i++) {
      Key key = keys[i];
      res[i] = new AbstractMap.SimpleImmutableEntry<>(key, map.get(key));
    }
    return res;
  }
}
