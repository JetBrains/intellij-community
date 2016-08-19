/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.persistent;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author irengrig
 */
public class SmallMapSerializer<K,V> implements Forceable {
  private final Map<KeyWrapper<K>,V> myMap;
  private final File myFile;
  private final KeyDescriptor<K> myKeyDescriptor;
  private final DataExternalizer<V> myValueExternalizer;
  private boolean myDirty;
  private Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.persistent.SmallMapSerializer");

  public SmallMapSerializer(final File file, final KeyDescriptor<K> keyDescriptor, final DataExternalizer<V> valueExternalizer) {
    myFile = file;
    myKeyDescriptor = keyDescriptor;
    myValueExternalizer = valueExternalizer;
    myMap = new HashMap<>();
    init();
  }

  private void init() {
    try {
      final byte[] bytes = FileUtil.loadFileBytes(myFile);
      final DataInputStream dis = new DataInputStream(new UnsyncByteArrayInputStream(bytes));
      final int size = dis.readInt();
      for (int i = 0; i < size; i++) {
        final KeyWrapper<K> keyWrapper = new KeyWrapper<>(myKeyDescriptor, myKeyDescriptor.read(dis));
        final V value = myValueExternalizer.read(dis);
        myMap.put(keyWrapper, value);
      }
    } catch (FileNotFoundException ignore) {
    } catch (IOException e) {
      LOG.error(e);
    }
  }

  public void put(final K key, final V value) {
    myMap.put(new KeyWrapper<>(myKeyDescriptor, key), value);
    myDirty = true;
  }

  public V get(final K key) {
    return myMap.get(new KeyWrapper<>(myKeyDescriptor, key));
  }

  @Override
  public void force() {
    if (! myDirty) return;
    try{
      final BufferExposingByteArrayOutputStream bos = new BufferExposingByteArrayOutputStream();
      final DataOutput out = new DataOutputStream(bos);
      out.writeInt(myMap.size());
      for (Map.Entry<KeyWrapper<K>, V> entry : myMap.entrySet()) {
        myKeyDescriptor.save(out, entry.getKey().myKey);
        myValueExternalizer.save(out, entry.getValue());
      }
      FileUtil.writeToFile(myFile, bos.getInternalBuffer(), 0, bos.size());
    } catch (IOException e) {
      LOG.error(e);
    } finally {
      myDirty = false;
    }
  }

  @Override
  public boolean isDirty() {
    return myDirty;
  }

  private static class KeyWrapper<K> {
    private final K myKey;
    private final KeyDescriptor<K> myDescriptor;

    private KeyWrapper(@NotNull final KeyDescriptor<K> descriptor, final K key) {
      myDescriptor = descriptor;
      myKey = key;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final KeyWrapper<K> that = (KeyWrapper) o;

      return myDescriptor.isEqual(this.myKey, that.myKey);
    }

    @Override
    public int hashCode() {
      return myDescriptor.getHashCode(myKey);
    }
  }

  public Collection<K> keySet() {
    final ArrayList<K> result = new ArrayList<>(myMap.size());
    for (KeyWrapper<K> keyWrapper : myMap.keySet()) {
      result.add(keyWrapper.myKey);
    }
    return result;
  }
}
