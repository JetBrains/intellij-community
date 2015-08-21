/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.util.containers.LRUConcurrentIntObjectMap;
import com.intellij.util.text.ByteArrayCharSequence;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class FileNameCache {
  private static final LRUConcurrentIntObjectMap<CharSequence> ourNameCache = new LRUConcurrentIntObjectMap<CharSequence>(65000);

  public static int storeName(@NotNull String name) {
    final int idx = FSRecords.getNameId(name);
    CharSequence rawName = ByteArrayCharSequence.convertToBytesIfAsciiString(name);
    ourNameCache.put(idx, rawName);
    return idx;
  }

  private static final LRUConcurrentIntObjectMap.IntFunction<CharSequence> getNameById = new LRUConcurrentIntObjectMap.IntFunction<CharSequence>() {
    @NotNull
    @Override
    public CharSequence apply(int id) {
      String name = FSRecords.getNameByNameId(id);
      return ByteArrayCharSequence.convertToBytesIfAsciiString(name);
    }
  };

  @NotNull
  public static CharSequence getVFileName(int nameId) {
    assert nameId > 0 : nameId;
    return ourNameCache.computeIfAbsent(nameId, getNameById);
  }
}
