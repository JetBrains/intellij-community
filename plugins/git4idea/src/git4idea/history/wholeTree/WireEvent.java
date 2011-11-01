/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history.wholeTree;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

/**
* @author irengrig
 *
 * commits with 1 start and end just belongs to its wire
*/
public class WireEvent implements WireEventI {
  private final int myCommitIdx;
  // wire # can be taken from commit
  @Nullable
  private int[] myCommitsEnds;      // branch point   |/.       -1 here -> start of a wire
  @Nullable
  private int[] myWireEnds;
  private int[] myFutureWireStarts;
  private int[] myCommitsStarts;    // merge commit   |\  parents here. -1 here -> no parents, i.e. break

  public WireEvent(final int commitIdx, final int[] commitsEnds) {
    myCommitIdx = commitIdx;
    myCommitsEnds = commitsEnds;
    myCommitsStarts = ArrayUtil.EMPTY_INT_ARRAY;
    myWireEnds = null;
    myFutureWireStarts = ArrayUtil.EMPTY_INT_ARRAY;
  }

  @Override
  public int getCommitIdx() {
    return myCommitIdx;
  }

  public void addStart(final int idx, int wireNumber) {
    assert myFutureWireStarts.length > 0 || idx == -1;
    myCommitsStarts = ArrayUtil.append(myCommitsStarts, idx);
    if (idx != -1) {
      if (myFutureWireStarts.length == 1) {
        assert myFutureWireStarts[0] == wireNumber;
        myFutureWireStarts = ArrayUtil.EMPTY_INT_ARRAY;
      } else {
        final int[] newArr = new int[myFutureWireStarts.length - 1];
        int j = 0;
        for (int i = 0; i < myFutureWireStarts.length; i++) {
          int futureWireStart = myFutureWireStarts[i];
          if (futureWireStart != wireNumber) {
            newArr[j] = futureWireStart;
            ++ j;
          }
        }
        myFutureWireStarts = newArr;
      }
    }
  }

  public int getWaitStartsNumber() {
    return myFutureWireStarts.length;
  }

  public int[] getFutureWireStarts() {
    return myFutureWireStarts;
  }

  public void setWaitStartsNumber(Integer[] waitStarts) {
    myFutureWireStarts = new int[waitStarts.length];
    for (int i = 0; i < waitStarts.length; i++) {
      Integer waitStart = waitStarts[i];
      myFutureWireStarts[i] = waitStart;
    }
  }

  public void addWireEnd(final int idx) {
    if (myWireEnds == null) {
      myWireEnds = new int[]{idx};
    } else {
      myWireEnds = ArrayUtil.append(myWireEnds, idx);
    }
  }

  public void setWireEnds(@Nullable int[] wireEnds) {
    myWireEnds = wireEnds;
  }

  @Override
  @Nullable
  public int[] getWireEnds() {
    return myWireEnds;
  }
  
  public void setCommitEnds(final int [] ends) {
    myCommitsEnds = ends;
  }

  @Override
  @Nullable
  public int[] getCommitsEnds() {
    return myCommitsEnds;
  }

  @Override
  public int[] getCommitsStarts() {
    return myCommitsStarts;
  }

  // no parent commit present in quantity or exists
  @Override
  public boolean isEnd() {
    return myCommitsStarts.length == 1 && myCommitsStarts[0] == -1;
  }

  @Override
  public boolean isStart() {
    return myCommitsEnds != null && myCommitsEnds.length == 1 && myCommitsEnds[0] == -1;
  }

  @Override
  public String toString() {
    return "WireEvent{" +
           "myCommitIdx=" + myCommitIdx +
           ", myCommitsEnds=" + ((myCommitsEnds == null) ? "null" : StringUtil.join(myCommitsEnds, ", ")) +
           ", myWireEnds=" + ((myWireEnds == null) ? "null" : StringUtil.join(myWireEnds, ", ")) +
           ", myCommitsStarts=" + ((myCommitsStarts == null) ? "null" : StringUtil.join(myCommitsStarts, ", ")) +
           '}';
  }
}
