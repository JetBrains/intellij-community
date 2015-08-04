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

import static org.junit.Assert.assertEquals;

/**
 * @author erokhins
 */
public class HashBuildTest {
  @Test
  public void testBuildNone() {
    doTest("");
  }

  @Test
  public void testBuild0() {
    doTest("0");
  }

  @Test
  public void testBuild000() {
    doTest("0000");
  }

  @Test
  public void testBuild1() {
    doTest("1");
  }

  @Test
  public void testBuildSomething() {
    doTest("ff01a");
  }

  @Test
  public void testBuildEven() {
    doTest("1133");
  }

  @Test
  public void testBuildOdd() {
    doTest("ffa");
  }

  @Test
  public void testBuildLongOdd() {
    doTest("ff01a123125afabcdef12345678900987654321");
  }

  @Test
  public void testBuildLongEven() {
    doTest("ff01a123125afabcdef123456789009876543219");
  }

  private static void doTest(String strHash) {
    Hash hash = HashImpl.build(strHash);
    assertEquals(strHash, hash.asString());
  }
}
