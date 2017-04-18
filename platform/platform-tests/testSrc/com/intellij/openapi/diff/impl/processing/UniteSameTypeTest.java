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
package com.intellij.openapi.diff.impl.processing;

import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.highlighting.FragmentEquality;
import com.intellij.openapi.diff.impl.highlighting.FragmentStringConvertion;
import com.intellij.util.Assertion;
import com.intellij.util.diff.FilesTooBigForDiffException;
import junit.framework.TestCase;

public class UniteSameTypeTest extends TestCase {
  private final Assertion CHECK = new Assertion(new FragmentStringConvertion());

  @Override
  public void setUp() throws Exception {
    super.setUp();
    CHECK.setEquality(new FragmentEquality());
  }

  public void testUnitDifferentOnesides() throws FilesTooBigForDiffException {
    DiffFragment[] fragments = UniteSameType.INSTANCE.correct(new DiffFragment[]{new DiffFragment("a", "b"),
                                                        new DiffFragment(null, " "),
                                                        new DiffFragment("\n ", null),
                                                        new DiffFragment("x", "x")});
    CHECK.compareAll(new DiffFragment[]{new DiffFragment("a\n ", "b "), new DiffFragment("x", "x")}, fragments);
  }

  public void testUniteEqualsUnitesFormattingOnly() throws FilesTooBigForDiffException {
    DiffFragment changed = new DiffFragment("abc", "123");
    DiffFragment equal = new DiffFragment("qqq", "qqq");
    DiffFragment[] fragments = DiffCorrection.UnitEquals.INSTANCE.correct(new DiffFragment[]{
      changed,
      new DiffFragment(" xxx", "xxx"), new DiffFragment("yyy", "  yyy"),
      equal});
    CHECK.compareAll(new DiffFragment[]{changed, new DiffFragment(" xxxyyy", "xxx  yyy"), equal}, fragments);
  }
}
