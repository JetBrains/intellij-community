package com.intellij.openapi.diff.impl;

import com.intellij.openapi.diff.BaseDiffTestCase;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.util.Assertion;

import java.io.IOException;

public class LineFragmentsTest extends BaseDiffTestCase {
  private final Assertion CHECK = new Assertion();

  public void testA() throws IOException {
    setFile1("lines1.txt");
    setFile2("lines2.txt");
    DiffPanelImpl diffPanel = loadFiles();
    CHECK.compareAll(new int[]{1, 2, 3}, diffPanel.getFragmentBeginnings(FragmentSide.SIDE1));
    CHECK.compareAll(new int[]{1, 2}, diffPanel.getFragmentBeginnings(FragmentSide.SIDE2));
  }

  public void testC() throws IOException {
    setFile1("linesC1.txt");
    setFile2("linesC2.txt");
    DiffPanelImpl diffPanel = loadFiles();
    checkTextEqual(content1(), getEditor1(diffPanel));
    checkTextEqual(content2(), getEditor2(diffPanel));
  }

  public void testD() throws IOException {
    setFile1("linesD1.txt");
    setFile2("linesD2.txt");
    DiffPanelImpl diffPanel = loadFiles();
    CHECK.compareAll(new int[]{1, 2}, diffPanel.getFragmentBeginnings(FragmentSide.SIDE1));
    CHECK.compareAll(new int[]{1, 2}, diffPanel.getFragmentBeginnings(FragmentSide.SIDE2));
  }

  public void testEmptyLine() throws IOException {
    setFile1("default/emptyLine.1");
    setFile2("default/emptyLine.2");
    DiffPanelImpl diffPanel = loadFiles();
    CHECK.compareAll(new int[]{1}, diffPanel.getFragmentBeginnings(FragmentSide.SIDE1));
    CHECK.compareAll(new int[]{1}, diffPanel.getFragmentBeginnings(FragmentSide.SIDE2));
  }

  public void testRestyleNewLines() {
    DiffPanelImpl diffPanel = createDiffPanel(null, myProject, false);
    setContents(diffPanel, "f(a, b);\n", "f(a,\n  b);\n");
    CHECK.singleElement(diffPanel.getFragmentBeginnings(FragmentSide.SIDE1), 0);
    CHECK.singleElement(diffPanel.getFragmentBeginnings(FragmentSide.SIDE2), 0);
  }
}
