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

import com.intellij.util.containers.SLRUMap;

import java.io.File;
import java.io.IOException;

public class PersistentStringEnumerator extends PersistentEnumerator<String>{
  private final SLRUMap<Integer, String> myIdToStringCache;
  private final SLRUMap<Integer, Integer> myHashcodeToIdCache;

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
      myIdToStringCache = new SLRUMap<Integer, String>(8192, 8192);
      myHashcodeToIdCache = new SLRUMap<Integer, Integer>(8192, 8192);
    } else {
      myIdToStringCache = null;
      myHashcodeToIdCache = null;
    }
  }

  @Override
  public int enumerate(String value) throws IOException {
    if (myHashcodeToIdCache != null && value != null) {
      Integer cachedId;
      synchronized (myHashcodeToIdCache) {
        cachedId = myHashcodeToIdCache.get(value.hashCode());
      }
      if (cachedId != null) {
        String s;
        synchronized (myIdToStringCache) {
          s = myIdToStringCache.get(cachedId.intValue());
        }
        if (s != null && value.equals(s)) return cachedId.intValue();
      }
    }

    int enumerate = super.enumerate(value);

    if (myHashcodeToIdCache != null && value != null) {
      synchronized (myHashcodeToIdCache) {
        myHashcodeToIdCache.put(value.hashCode(), enumerate);
      }
    }
    return enumerate;
  }

  @Override
  protected int enumerateImpl(String value, boolean saveNewValue) throws IOException {
    int idx = super.enumerateImpl(value, saveNewValue);

    if (myIdToStringCache != null) {
      synchronized (myIdToStringCache) {
        myIdToStringCache.put(idx, value);
      }
    }

    return idx;
  }

  @Override
  public String valueOf(int idx) throws IOException {
    if (myIdToStringCache != null) {
      synchronized (myIdToStringCache) {
        String s = myIdToStringCache.get(idx);
        if (s != null) return s;
      }
    }
    return super.valueOf(idx);
  }

  @Override
  protected void markCorrupted() {
    super.markCorrupted();

    if (myIdToStringCache != null) {
      synchronized (myIdToStringCache) {
        myIdToStringCache.clear();
      }
    }

    if (myHashcodeToIdCache != null) {
      synchronized (myHashcodeToIdCache) {
        myHashcodeToIdCache.clear();
      }
    }
  }

  @Override
  protected void doClose() throws IOException {
    super.doClose();

    if (myIdToStringCache != null) {
      synchronized (myIdToStringCache) {
        myIdToStringCache.clear();
      }
    }

    if (myHashcodeToIdCache != null) {
      synchronized (myHashcodeToIdCache) {
        myHashcodeToIdCache.clear();
      }
    }
  }
}
