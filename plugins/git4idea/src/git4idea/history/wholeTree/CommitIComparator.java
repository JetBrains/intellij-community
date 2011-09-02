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
package git4idea.history.wholeTree;

import com.intellij.openapi.util.Comparing;

import java.util.Comparator;

/**
* Created by IntelliJ IDEA.
* User: Irina.Chernushina
* Date: 9/1/11
* Time: 12:54 PM
*/
public class CommitIComparator implements Comparator<CommitI> {
  private final static CommitIComparator ourInstance = new CommitIComparator();

  public static CommitIComparator getInstance() {
    return ourInstance;
  }

  @Override
  public int compare(CommitI o1, CommitI o2) {
    final Integer rep1 = o1.selectRepository(SelectorList.getInstance());
    final Integer rep2 = o2.selectRepository(SelectorList.getInstance());

    if (rep1.equals(rep2)) return -1;

    long result = o1.getTime() - o2.getTime();
    if (result == 0) {
      if (Comparing.equal(o1.getHash(), o2.getHash())) return 0;
      return -1;

      /*final Integer rep1 = o1.selectRepository(SelectorList.getInstance());
      final Integer rep2 = o2.selectRepository(SelectorList.getInstance());
      result = rep1 - rep2;

      if (result == 0) {
        return -1;  // actually, they are still not equal -> keep order
      }*/
    }
    // descending
    return result == 0 ? 0 : (result < 0 ? 1 : -1);
  }
}
