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

public class NormalizationTest extends TestCase {
  private final Assertion CHECK = new Assertion(new FragmentStringConvertion());

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    CHECK.setEquality(new FragmentEquality());
  }

  public void testSingleSide() throws FilesTooBigForDiffException {
    DiffCorrection correction = DiffCorrection.Normalize.INSTANCE;
    DiffFragment[] corrected = correction.correct(
        new DiffFragment[]{new DiffFragment(null, "a"),
                           new DiffFragment("b", null),
                           new DiffFragment("c", "d"),
                           new DiffFragment(null, "a"),
                           new DiffFragment("b", null),
                           new DiffFragment("1", null),
                           new DiffFragment("x", "x"),
                           new DiffFragment(null, "a")});
    CHECK.compareAll(new DiffFragment[]{new DiffFragment("b", "a"),
                                        new DiffFragment("c", "d"),
                                        new DiffFragment("b1", "a"),
                                        new DiffFragment("x", "x"), new DiffFragment(null, "a")},
                     corrected);
  }

  public void testUnitesEquals() throws FilesTooBigForDiffException {
    DiffCorrection correction = DiffCorrection.Normalize.INSTANCE;
    DiffFragment[] fragments = correction.correct(new DiffFragment[]{new DiffFragment(null, "a"),
                                            new DiffFragment("x", "x"),
                                            new DiffFragment("y", "y"),
                                            new DiffFragment("z", null), new DiffFragment(null, "z")});
    CHECK.compareAll(new DiffFragment[]{new DiffFragment(null, "a"), new DiffFragment("xyz", "xyz")}, fragments);
  }
}
