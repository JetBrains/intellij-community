package com.intellij.execution.junit2.states;

import com.intellij.execution.junit2.segments.ObjectReader;

public class JUnitDiffHyperlink extends DiffHyperlink {

  public JUnitDiffHyperlink(final String expected, final String actual, final String filePath) {
    super(expected, actual, filePath); 
  }

  public static DiffHyperlink readFrom(final ObjectReader reader) {
    final String expected = reader.readLimitedString();
    final String actual = reader.readLimitedString();
    final String fileName = reader.isAtEnd() ? null : reader.readLimitedString();
    return new JUnitDiffHyperlink(expected, actual, fileName);
  }

}
