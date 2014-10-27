/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.vcs.log.graph.impl.print;

import com.intellij.vcs.log.graph.AbstractTestWithTwoTextFile;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.parser.LinearGraphParser;
import org.junit.Test;

import java.io.IOException;

import static com.intellij.vcs.log.graph.GraphStrUtils.edgesInRowToStr;
import static org.junit.Assert.assertEquals;

public class EdgesInRowTest extends AbstractTestWithTwoTextFile {

  public EdgesInRowTest() {
    super("edgesInRow");
  }

  @Override
  protected void runTest(String in, String out) {
    LinearGraph graph = LinearGraphParser.parse(in);
    EdgesInRowGenerator edgesInRowGenerator = new EdgesInRowGenerator(graph);
    assertEquals(out, edgesInRowToStr(edgesInRowGenerator, graph.nodesCount()));
  }

  @Test
  public void simple() throws IOException {
    doTest("simple");
  }

  @Test
  public void manyNodes() throws IOException {
    doTest("manyNodes");
  }

  @Test
  public void manyUpNodes() throws IOException {
    doTest("manyUpNodes");
  }

  @Test
  public void manyDownNodes() throws IOException {
    doTest("manyDownNodes");
  }

  @Test
  public void oneNode() throws IOException {
    doTest("oneNode");
  }

  @Test
  public void oneNodeNotFullGraph() throws IOException {
    doTest("oneNodeNotFullGraph");
  }

  @Test
  public void notFullGraph() throws IOException {
    doTest("notFullGraph");
  }

  @Test
  public void longGraph() throws IOException {
    doTest("longGraph");
  }

  @Test
  public void notLoadNode() throws IOException {
    doTest("notLoadNode");
  }

}
