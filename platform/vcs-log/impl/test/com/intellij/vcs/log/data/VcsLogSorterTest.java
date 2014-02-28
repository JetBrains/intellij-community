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

  @Test
  public void simpleTest() {
    List<TimedVcsCommit> log = log("6|-a2|-a0", "3|-a1|-a0", "1|-a0|-");
    List<TimedVcsCommit> sorted = new VcsLogSorter<TimedVcsCommit>().sortByDateTopoOrder(log);
    List<TimedVcsCommit> expected = log("6|-a2|-a0", "3|-a1|-a0", "1|-a0|-");
    assertEquals(expected, sorted);
  }

}
