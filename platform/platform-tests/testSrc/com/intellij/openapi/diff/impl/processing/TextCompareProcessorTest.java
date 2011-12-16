package com.intellij.openapi.diff.impl.processing;

import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.diff.impl.fragments.LineFragment;
import com.intellij.util.diff.FilesTooBigForDiffException;
import junit.framework.TestCase;

import java.util.ArrayList;

public class TextCompareProcessorTest extends TestCase {
  public void testIgnoreWrappingEqualText() throws FilesTooBigForDiffException {
    TextCompareProcessor processor = new TextCompareProcessor(ComparisonPolicy.IGNORE_SPACE);
    ArrayList<LineFragment> lineFragments = processor.process("f(a, b)\n", "f(a,\nb)\n");
    assertTrue(lineFragments.size() == 1);
    assertNull(lineFragments.get(0).getType());
  }
}
