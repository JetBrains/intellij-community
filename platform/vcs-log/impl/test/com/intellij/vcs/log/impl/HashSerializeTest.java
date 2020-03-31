// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.DataOutputStream;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.*;
import java.util.ArrayList;
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
  private static File writeToTempFile(HashImpl @NotNull ... hashes) throws IOException {
    File file = FileUtil.createTempFile("", "");
    try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
      for (HashImpl hash : hashes) {
        hash.write(out);
      }
    }
    return file;
  }

  @NotNull
  private static List<HashImpl> readFromFile(@NotNull File file) throws IOException {
    List<HashImpl> result = new ArrayList<>();
    try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
      while (in.available() > 0) {
        result.add((HashImpl)HashImpl.read(in));
      }
    }
    return result;
  }
}
