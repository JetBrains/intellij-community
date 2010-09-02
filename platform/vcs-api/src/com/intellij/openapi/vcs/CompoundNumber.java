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
package com.intellij.openapi.vcs;

/**
* @author irengrig
*/
public class CompoundNumber {
  private final int myMemberNumber;
  private final int myIdx;

  public CompoundNumber(int memberNumber, int idx) {
    myIdx = idx;
    myMemberNumber = memberNumber;
  }

  public int getIdx() {
    return myIdx;
  }

  public int getMemberNumber() {
    return myMemberNumber;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    CompoundNumber that = (CompoundNumber)o;

    if (myIdx != that.myIdx) return false;
    if (myMemberNumber != that.myMemberNumber) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myMemberNumber;
    result = 31 * result + myIdx;
    return result;
  }
}
