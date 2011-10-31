/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.tests;

import com.intellij.openapi.vcs.BigArray;
import com.intellij.openapi.vcs.ComparableComparator;
import com.intellij.openapi.vcs.GroupingMerger;
import junit.framework.Assert;
import junit.framework.TestCase;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 10/19/11
 * Time: 3:16 PM
 */
public class GroupingMergerTest extends TestCase {
  public void testSimple() throws Exception {
    final Map<Integer, Integer> recalculation = new HashMap<Integer, Integer>();
    // 2nd - index
    final Map<Integer, Integer> newInsertions = new HashMap<Integer, Integer>();

    final GroupingMerger<Integer, String> merger = new GroupingMerger<Integer, String>() {
      @Override
      protected void willBeRecountFrom(int idx, int wasSize) {
      }

      @Override
      protected String getGroup(Integer integer) {
        return "";
      }

      @Override
      protected Integer wrapGroup(String s, Integer item) {
        return -1;
      }

      @Override
      protected void oldBecame(int was, int is) {
        recalculation.put(was, is);
      }

      @Override
      protected void afterConsumed(Integer integer, int i) {
        newInsertions.put(integer, i);
      }
    };

    final BigArray<Integer> main = new BigArray<Integer>(4);
    main.add(10);
    main.add(20);
    main.add(30);
    main.add(40);
    main.add(50);
    main.add(60);
    main.add(70);
    main.add(80);
    main.add(90);
    main.add(100);
    main.add(110);

    final BigArray<Integer> insert = new BigArray<Integer>(4);
    insert.add(11);
    insert.add(21);
    insert.add(31);
    insert.add(41);
    insert.add(51);
    insert.add(61);
    insert.add(71);
    insert.add(81);
    insert.add(91);
    insert.add(101);
    insert.add(111);

    merger.firstPlusSecond(main, insert, new ComparableComparator<Integer>(), -1);

    int added = 1;
    // 20-110
    for (int i = 1; i <= 10; i++) {
      final Integer integer = recalculation.get(i);
      Assert.assertEquals(i + added, (int)integer);
      ++ added;
    }
    int startIdx = 1;
    for (int i = 11; i <= 111; i+=10) {
      Assert.assertEquals(startIdx, (int)newInsertions.get(i));
      startIdx += 2;
    }
  }
}
