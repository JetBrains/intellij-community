package com.intellij.vcs.log.printmodel.cells.builder;

import com.intellij.vcs.log.GraphCommit;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.graph.GraphTestUtils;
import com.intellij.vcs.log.parser.SimpleCommitListParser;
import com.intellij.vcs.log.printmodel.layout.LayoutModel;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;

import static com.intellij.vcs.log.printmodel.LayoutTestUtils.toStr;
import static junit.framework.Assert.assertEquals;

/**
 * @author erokhins
 */
public class LayoutModelBuilderTest {
  private void runTest(String input, String out) throws IOException {
    SimpleCommitListParser parser = new SimpleCommitListParser(new StringReader(input));
    List<GraphCommit> vcsCommitParentses = parser.readAllCommits();
    LayoutModel layoutModel = new LayoutModel(GraphTestUtils.buildGraph(vcsCommitParentses, Collections.<VcsRef>emptyList()));
    assertEquals(out, toStr(layoutModel));
  }

  @Test
  public void test1() throws IOException {
    runTest("a0|-a3 a1\n" +
            "a1|-a2 a4\n" +
            "a2|-a3 a5 a8\n" +
            "a3|-a6\n" +
            "a4|-a7\n" +
            "a5|-a7\n" +
            "a6|-a7\n" +
            "a7|-\n" +
            "a8|-",

            "a0\n" +
            "a0:a3 a1\n" +
            "a0:a3 a1:a4 a2\n" +
            "a3 a1:a4 a2:a8 a2:a5\n" +
            "a3:a6 a4 a2:a8 a2:a5\n" +
            "a3:a6 a4:a7 a2:a8 a5\n" +
            "a6 a7 a2:a8\n" +
            "a7 a2:a8\n" +
            "a8");

  }


}
