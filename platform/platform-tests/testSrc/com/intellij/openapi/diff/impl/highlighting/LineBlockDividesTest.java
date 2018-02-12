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
import com.intellij.util.Assertion;
import junit.framework.TestCase;

public class LineBlockDividesTest extends TestCase {
  private final Assertion CHECK = new Assertion(new FragmentStringConvertion());

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    CHECK.setEquality(new FragmentEquality());
  }

  public void testSingleSide() {
    DiffFragment abc_ = new DiffFragment("abc", null);
    DiffFragment xyzL_ = new DiffFragment("xyz\n", null);
    DiffFragment x_y = new DiffFragment("x", "y");
    DiffFragment a_b = new DiffFragment("a", "b");
    DiffFragment xyzL_L = new DiffFragment("xyz\n", "\n");
    DiffFragment abcL_ = new DiffFragment("abc\n", null);
    DiffFragment[][] lineBlocks = LineBlockDivider.SINGLE_SIDE.divide(new DiffFragment[]{
      abc_, xyzL_,
      x_y, a_b, xyzL_L,
      abcL_});
    CHECK.compareAll(new DiffFragment[][]{
      new DiffFragment[]{abc_, xyzL_}, new DiffFragment[]{x_y, a_b, xyzL_L}, new DiffFragment[]{abcL_}},
                     lineBlocks);
  }
}
