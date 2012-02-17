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
package com.intellij.util.io;

import com.intellij.util.containers.ConcurrentSLRUMap;

import java.io.File;
import java.io.IOException;

public class PersistentStringEnumerator extends PersistentEnumeratorDelegate<String>{
  private final ConcurrentSLRUMap<Integer, String> myIdToStringCache;
  private final ConcurrentSLRUMap<Integer, Integer> myHashcodeToIdCache;

  public PersistentStringEnumerator(final File file) throws IOException {
    this(file, 1024 * 4);
  }

  public PersistentStringEnumerator(final File file, boolean cacheLastMappings) throws IOException {
    this(file, 1024 * 4, cacheLastMappings);
  }

  public PersistentStringEnumerator(final File file, final int initialSize) throws IOException {
    this(file, initialSize, false);
  }

  private PersistentStringEnumerator(final File file, final int initialSize, boolean cacheLastMappings) throws IOException {
    super(file, new EnumeratorStringDescriptor(), initialSize);
    if (cacheLastMappings) {
      myIdToStringCache = new ConcurrentSLRUMap<Integer, String>(8192, 8192);
      myHashcodeToIdCache = new ConcurrentSLRUMap<Integer, Integer>(8192, 8192);
    } else {
      myIdToStringCache = null;
      myHashcodeToIdCache = null;
    }
  }

  @Override
  public int enumerate(String value) throws IOException {
    int valueHashCode = -1;

    if (myHashcodeToIdCache != null && value != null) {
      Integer cachedId = myHashcodeToIdCache.get(valueHashCode = value.hashCode());
      if (cachedId != null) {
        String s = myIdToStringCache.get(cachedId);
        if (s != null && value.equals(s)) return cachedId.intValue();
      }
    }

    int enumerate = super.enumerate(value);
    Integer enumeratedInteger = null;

    if (myHashcodeToIdCache != null) {
      enumeratedInteger = enumerate;
      if (value != null) myHashcodeToIdCache.put(valueHashCode, enumeratedInteger);
    }

    if (myIdToStringCache != null) {
      myIdToStringCache.put(enumeratedInteger, value);
    }
    return enumerate;
  }

  @Override
  public String valueOf(int idx) throws IOException {
    if (myIdToStringCache != null) {
      String s = myIdToStringCache.get(idx);
      if (s != null) return s;
    }
    String s = super.valueOf(idx);
    if (myIdToStringCache != null && s != null) {
      myIdToStringCache.put(idx, s);
    }
    return s;
  }

  @Override
  public void close() throws IOException {
    super.close();

    if (myIdToStringCache != null) {
      myIdToStringCache.clear();
    }

    if (myHashcodeToIdCache != null) {
      myHashcodeToIdCache.clear();
    }
  }

  public void markCorrupted() {
    myEnumerator.markCorrupted();
  }
}
