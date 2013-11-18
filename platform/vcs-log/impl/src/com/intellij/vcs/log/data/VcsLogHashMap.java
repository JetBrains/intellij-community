/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.vcs.log.data;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.Page;
import com.intellij.util.io.PersistentEnumerator;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.HashImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;

/**
 * Supports the int <-> Hash persistent mapping.
 */
class VcsLogHashMap {

  private static final File LOG_CACHE_APP_DIR = new File(PathManager.getSystemPath(), "vcs-log");

  private final PersistentEnumerator<Hash> myPersistentEnumerator;

  VcsLogHashMap(@NotNull Project project) throws IOException {
    File myMapFile = new File(LOG_CACHE_APP_DIR, project.getName() + "." + project.getLocationHash());
    myPersistentEnumerator = new PersistentEnumerator<Hash>(myMapFile, new MyHashKeyDescriptor(), Page.PAGE_SIZE);
  }

  @Nullable
  Hash getHash(int index) throws IOException {
    return myPersistentEnumerator.valueOf(index);
  }

  int getOrPut(@NotNull Hash hash) throws IOException {
    return myPersistentEnumerator.enumerate(hash);
  }

  private static class MyHashKeyDescriptor implements KeyDescriptor<Hash> {
    @Override
    public void save(DataOutput out, Hash value) throws IOException {
      out.writeUTF(value.asString());
    }

    @Override
    public Hash read(DataInput in) throws IOException {
      return HashImpl.build(in.readUTF());
    }

    @Override
    public int getHashCode(Hash value) {
      return value.hashCode();
    }

    @Override
    public boolean isEqual(Hash val1, Hash val2) {
      return val1.equals(val2);
    }
  }
}
