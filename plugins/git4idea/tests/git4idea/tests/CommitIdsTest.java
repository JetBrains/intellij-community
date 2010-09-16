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
package git4idea.tests;

import git4idea.history.wholeTree.CommitIdsHolder;
import junit.framework.Assert;
import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author irengrig
 */
public class CommitIdsTest extends TestCase {
  public void testSimple() throws Exception {
    final CommitIdsHolder<Integer> holder = new CommitIdsHolder<Integer>();

    holder.add(Arrays.asList(1,2,3,4,5,6,7,8));
    Assert.assertTrue(holder.haveData());
    Collection<Integer> integers = holder.get(5);
    Assert.assertEquals(5, integers.size());
    Assert.assertTrue(holder.haveData());
    integers = holder.get(20);
    Assert.assertEquals(3, integers.size());
    Assert.assertTrue(! holder.haveData());
  }
}
