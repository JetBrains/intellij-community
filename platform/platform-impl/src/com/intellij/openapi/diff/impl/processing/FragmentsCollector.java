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
package com.intellij.openapi.diff.impl.processing;

import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.fragments.Fragment;
import com.intellij.openapi.diff.impl.fragments.InlineFragment;
import com.intellij.openapi.util.TextRange;

import java.util.ArrayList;

class FragmentsCollector {
  private final ArrayList<Fragment> myFragments = new ArrayList<Fragment>();
  private int myOffset1 = 0;
  private int myOffset2 = 0;

  public Fragment addDiffFragment(DiffFragment fragment) {
    int length1 = LineFragmentsCollector.getLength(fragment.getText1());
    int length2 = LineFragmentsCollector.getLength(fragment.getText2());
    InlineFragment inlineFragment = new InlineFragment(LineFragmentsCollector.getType(fragment),
                                         new TextRange(myOffset1, myOffset1 + length1),
                                         new TextRange(myOffset2, myOffset2 + length2));
    myFragments.add(inlineFragment);
    myOffset1 += length1;
    myOffset2 += length2;
    return inlineFragment;
  }

  public ArrayList<Fragment> getFragments() { return myFragments; }
}
