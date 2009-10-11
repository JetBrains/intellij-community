/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.fragments;

import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.EmptyIterator;

import java.util.Iterator;

public interface FragmentList {
  FragmentList shift(TextRange rangeShift1, TextRange rangeShift2,
                            int startLine1, int startLine2);

  FragmentList EMPTY = new FragmentList() {
    public FragmentList shift(TextRange rangeShift1, TextRange rangeShift2, int startLine1, int startLine2) {
      return EMPTY;
    }

    public boolean isEmpty() {
      return true;
    }

    public Iterator<Fragment> iterator() {
      return EmptyIterator.getInstance();
    }

    public Fragment getFragmentAt(int offset, FragmentSide side, Condition<Fragment> condition) {
      return null;
    }
  };

  boolean isEmpty();

  Iterator<Fragment> iterator();

  Fragment getFragmentAt(int offset, FragmentSide side, Condition<Fragment> condition);
}
