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
package com.intellij.vcs.log.data;

import com.intellij.vcs.log.TimedVcsCommit;
import org.junit.Test;

import java.util.List;

import static com.intellij.vcs.log.TimedCommitParser.log;
import static org.junit.Assert.assertEquals;

public class VcsLogSorterTest {

  private static String toStr(List<? extends TimedVcsCommit> commits) {
    StringBuilder s = new StringBuilder();
    for (TimedVcsCommit commit : commits) {
      if (s.length() != 0) {
        s.append(", ");
      }
      s.append(commit.getId().asString());
    }
    return s.toString();
  }

  private static void doTest(List<TimedVcsCommit> started, List<TimedVcsCommit> expected) {
    List<TimedVcsCommit> sorted = VcsLogSorter.sortByDateTopoOrder(started);
    assertEquals(toStr(expected), toStr(sorted));
  }

  @Test
  public void simpleTest() {
    doTest(log("1|-a0|-", "3|-a1|-a0", "6|-a2|-a0"),
           log("6|-a2|-a0", "3|-a1|-a0", "1|-a0|-"));
  }

  @Test
  public void severalHeads() {
    doTest(log(
      "1|-a4|-",
      "2|-a3|-a4",
      "6|-b1|-b2",
      "4|-b2|-a3",
      "3|-a2|-a3",
      "5|-a1|-a2"
    ),

           log("6|-b1|-b2",
               "5|-a1|-a2",
               "4|-b2|-a3",
               "3|-a2|-a3",
               "2|-a3|-a4",
               "1|-a4|-"));
  }

  @Test
  public void withMerge() {
    doTest(log(
      "6|-b1|-b2",
      "2|-a3|-a4",
      "3|-a2|-a3",
      "4|-b2|-a3",
      "5|-a1|-a2",
      "1|-a0|-b1 a1",
      "1|-a4|-"
    ),

           log("1|-a0|-b1 a1",
               "6|-b1|-b2",
               "5|-a1|-a2",
               "4|-b2|-a3",
               "3|-a2|-a3",
               "2|-a3|-a4",
               "1|-a4|-"));
  }

  @Test
  public void severalBranches() {
    doTest(log(
      "1|-a1|-",
      "3|-a3|-a1",
      "2|-a2|-",
      "4|-a4|-a2"
      ),
    log("4|-a4|-a2",
        "3|-a3|-a1",
        "2|-a2|-",
        "1|-a1|-"
    ));
  }

}
