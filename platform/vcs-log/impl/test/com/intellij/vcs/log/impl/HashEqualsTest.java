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
package com.intellij.vcs.log.impl;

import com.intellij.vcs.log.Hash;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author erokhins
 */
public class HashEqualsTest {
  @Test
  public void testEqualsSelf() {
    Hash hash = HashImpl.build("adf");
    assertThat(hash).isEqualTo(hash);
  }

  @Test
  public void testEqualsNull() {
    Hash hash = HashImpl.build("adf");
    assertThat(hash).isNotNull();
  }

  @Test
  public void testEquals() {
    Hash hash1 = HashImpl.build("adf");
    Hash hash2 = HashImpl.build("adf");
    assertThat(hash1).isEqualTo(hash2);
  }

  @Test
  public void testEqualsNone() {
    Hash hash1 = HashImpl.build("");
    Hash hash2 = HashImpl.build("");
    assertThat(hash1).isEqualTo(hash2);
  }
}
