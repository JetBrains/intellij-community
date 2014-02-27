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

package com.intellij.vcs.log.newgraph.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.vcs.log.newgraph.GraphFlags;
import com.intellij.vcs.log.newgraph.utils.Flags;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GraphFlagsTest {

  private static String flagsToStr(Flags flags) {
    StringBuilder s = new StringBuilder();
    for (int i = 0; i < flags.size(); i++) {
      if (flags.get(i)) {
        s.append("1");
      } else {
        s.append("0");
      }
    }
    return s.toString();
  }

  private static void setFlags(Flags flags, String flagsStr) {
    for (int i = 0; i < flagsStr.length(); i++) {
      flags.set(i, flagsStr.charAt(i) == '1');
    }
  }

  private static class Tester {
    private final GraphFlags myGraphFlags;
    private String myVisibleFlags;
    private String myEdgesFlags;

    public static Tester newTester(int size) {
      Tester tester = new Tester(new GraphFlags(size), StringUtil.repeat("0", size), StringUtil.repeat("0", size));

      tester.testAll();

      return tester;
    }

    private Tester(GraphFlags graphFlags, String visibleFlags, String edgesFlags) {
      myGraphFlags = graphFlags;
      myVisibleFlags = visibleFlags;
      myEdgesFlags = edgesFlags;
    }

    private void testAll() {
      testVisibleFlags();
      testEdgesFlags();
    }

    private void testVisibleFlags() {
      assertEquals(myVisibleFlags, flagsToStr(myGraphFlags.getVisibleNodes()));
    }

    private void testEdgesFlags() {
      assertEquals(myEdgesFlags, flagsToStr(myGraphFlags.getSimpleNodeFlags()));
    }

    public void newVisibleFlags(String visibleFlags) {
      myVisibleFlags = visibleFlags;
      setFlags(myGraphFlags.getVisibleNodes(), visibleFlags);

      testAll();
    }

    public void newEdgesFlags(String edgesFlags) {
      myEdgesFlags = edgesFlags;
      setFlags(myGraphFlags.getSimpleNodeFlags(), edgesFlags);

      testAll();
    }
  }


  @Test
  public void simpleTest() {
    Tester tester = Tester.newTester(5);

    tester.newEdgesFlags("00110");

    tester.newVisibleFlags("01000");

    tester.newEdgesFlags("01011");
  }

  @Test
  public void test1() {
    Tester tester = Tester.newTester(1);

    tester.newEdgesFlags("1");
    tester.newEdgesFlags("1");

    tester.newEdgesFlags("0");
    tester.newEdgesFlags("0");

    tester.newEdgesFlags("1");
    tester.newEdgesFlags("0");

    tester.newEdgesFlags("1");
    tester.newVisibleFlags("1");

    tester.newEdgesFlags("1");
    tester.newVisibleFlags("0");

    tester.newEdgesFlags("0");
    tester.newVisibleFlags("1");

    tester.newEdgesFlags("0");
    tester.newVisibleFlags("0");
  }

}
