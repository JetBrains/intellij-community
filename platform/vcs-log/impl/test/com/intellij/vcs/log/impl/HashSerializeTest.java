/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.DataOutputStream;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.*;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class HashSerializeTest {

  @Test
  public void full_hash() throws IOException {
    HashImpl hash = (HashImpl)HashImpl.build("d35ee91fad4a04bce0ea91a762cc8f3bf3e1929f");
    File file = writeToTempFile(hash);
    HashImpl newHash = readFromFile(file).get(0);
    assertEquals(hash, newHash);
  }

  @Test
  public void short_hash() throws IOException {
    HashImpl hash = (HashImpl)HashImpl.build("d35ee91");
    File file = writeToTempFile(hash);
    HashImpl newHash = readFromFile(file).get(0);
    assertEquals(hash, newHash);
  }
  
  @Test
  public void two_different_hashes() throws IOException {
    HashImpl hash1 = (HashImpl)HashImpl.build("d35ee91");
    HashImpl hash2 = (HashImpl)HashImpl.build("d35ee91fad4a04bce0ea91a762cc8f3bf3e1929f");
    File file = writeToTempFile(hash1, hash2);
    List<HashImpl> hashes = readFromFile(file);
    assertEquals(Arrays.asList(hash1, hash2), hashes);

  }

  @NotNull
  private static File writeToTempFile(@NotNull HashImpl... hashes) throws IOException {
    File file = FileUtil.createTempFile("", "");
    DataOutputStream out = new DataOutputStream(new FileOutputStream(file));
    try {
      for (HashImpl hash : hashes) {
        hash.write(out);
      }
    }
    finally {
      out.close();
    }
    return file;
  }

  @NotNull
  private static List<HashImpl> readFromFile(@NotNull File file) throws IOException {
    List<HashImpl> result = ContainerUtil.newArrayList();
    DataInputStream in = new DataInputStream(new FileInputStream(file));
    try {
      while (in.available() > 0) {
        result.add((HashImpl)HashImpl.read(in));
      }
    }
    finally {
      in.close();
    }
    return result;
  }
}
