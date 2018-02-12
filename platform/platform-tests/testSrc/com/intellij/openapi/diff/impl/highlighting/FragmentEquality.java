/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.highlighting;

import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.util.Comparing;
import gnu.trove.Equality;

public class FragmentEquality implements Equality {
  @Override
  public boolean equals(Object o1, Object o2) {
    DiffFragment fragment1 = (DiffFragment) o1;
    DiffFragment fragment2 = (DiffFragment) o2;
    return Comparing.equal(fragment1.getText1(), fragment2.getText1()) &&
           Comparing.equal(fragment1.getText2(), fragment2.getText2()) &&
           fragment1.isModified() == fragment2.isModified();
  }
}
