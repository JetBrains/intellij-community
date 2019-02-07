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
import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.highlighting.Util;
import com.intellij.util.diff.FilesTooBigForDiffException;
import junit.framework.TestCase;

public class LineBlocksDiffPolicyTest extends TestCase {
  public void test() throws FilesTooBigForDiffException {
    DiffPolicy.LineBlocks diffPolicy = new DiffPolicy.LineBlocks(ComparisonPolicy.DEFAULT);
    checkPolicy(diffPolicy, "abc\n123\n", "ABC\nXYZ\n");
    checkPolicy(diffPolicy, "abc\n123", "ABC\nXYZ");
    checkPolicy(diffPolicy, "abc\n123\n", "ABC\nXYZ");
  }

  private static void checkPolicy(DiffPolicy.LineBlocks diffPolicy, String text1, String text2) throws FilesTooBigForDiffException {
    DiffFragment[] fragments = diffPolicy.buildFragments(text1, text2);
    assertEquals(text1, Util.getText(fragments, FragmentSide.SIDE1).toString());
    assertEquals(text2, Util.getText(fragments, FragmentSide.SIDE2).toString());
  }
}
