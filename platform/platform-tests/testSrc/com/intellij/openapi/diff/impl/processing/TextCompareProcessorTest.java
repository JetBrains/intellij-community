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

import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.diff.impl.fragments.LineFragment;
import com.intellij.util.diff.FilesTooBigForDiffException;
import junit.framework.TestCase;

import java.util.List;

public class TextCompareProcessorTest extends TestCase {
  public void testIgnoreWrappingEqualText() throws FilesTooBigForDiffException {
    TextCompareProcessor processor = new TextCompareProcessor(ComparisonPolicy.IGNORE_SPACE);
    List<LineFragment> lineFragments = processor.process("f(a, b)\n", "f(a,\nb)\n");
    assertEquals(1, lineFragments.size());
    assertNull(lineFragments.get(0).getType());
  }
}
