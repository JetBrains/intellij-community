/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.execution.filters;

import com.intellij.openapi.project.Project;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RegexpFilterTest {
  private static final String FILE = RegexpFilter.FILE_PATH_MACROS;
  private static final String LINE = RegexpFilter.LINE_MACROS;
  private static final String COLUMN = RegexpFilter.COLUMN_MACROS;

  private RegexpFilter myFilter;

  @Test
  public void test() {
    createFilter(FILE);
    String line = "C:\\Work\\SampleProjects\\makeOutput.cmd";
    Filter.Result result = applyFilter(line);
    assertEquals(0, result.highlightStartOffset);
    assertEquals(line.length(), result.highlightEndOffset);
    HLInfo info = (HLInfo)result.hyperlinkInfo;
    info.checkInfo(line, 0, 0);
  }

  @Test
  public void testFileLineColumn() {
    createFilter(FILE + "x" + LINE + "y" + COLUMN);
    String fileName = "C:\\file";
    Filter.Result result = applyFilter(fileName + "x12y34");
    assertEquals(0, result.highlightStartOffset);
    assertEquals(fileName.length(), result.highlightEndOffset);
    HLInfo info = (HLInfo) result.hyperlinkInfo;
    info.checkInfo(fileName, 11, 33);
  }

  @Test
  public void testFileLineColumnWithTail() {
    createFilter(FILE + ":" + LINE + ":" + COLUMN + ".*");
    String fileName = "C:/file";
    Filter.Result result = applyFilter(fileName + ":12:34.1tail2");
    assertEquals(0, result.highlightStartOffset);
    assertEquals(fileName.length(), result.highlightEndOffset);
    HLInfo info = (HLInfo) result.hyperlinkInfo;
    info.checkInfo(fileName, 11, 33);
  }

  @Test
  public void test4814() {
    createFilter(FILE + ":" + LINE + ":" + COLUMN + ".*");
    String fileName = "C:/work/dev/C3C/V9_9_9_9/src/java/strata/p_switch/ss5e/dataSync/BFGParser.java";
    String line = fileName + ":544:13:544:13:";
    Filter.Result result = applyFilter(line);
    HLInfo info = (HLInfo) result.hyperlinkInfo;
    info.checkInfo(fileName, 543, 12);
  }

  @Test
  public void testWithSpaces() {
    createFilter("$FILE_PATH$\\s+\\($LINE$\\:$COLUMN$\\)");
    String line = "C:\\d ir\\file.ext (1:2)message";
    Filter.Result result = applyFilter(line);
    HLInfo info = (HLInfo)result.hyperlinkInfo;
    info.checkInfo("C:\\d ir\\file.ext", 0, 1);
  }

  private Filter.Result applyFilter(String line) {
    return myFilter.applyFilter(line, line.length());
  }

  private void createFilter(String string) {
    myFilter = new RegexpFilter(null, string){
      @Override
      protected HyperlinkInfo createOpenFileHyperlink(String filePath, int line, int column) {
        return createOpenFile(filePath, line,  column);
      }
    };
  }

  private static HyperlinkInfo createOpenFile(String fileName, int line, int column) {
    return new HLInfo(fileName, line, column);
  }

  private static class HLInfo implements HyperlinkInfo {
    public String myFileName;
    public int myLine;
    public int myColumn;

    public HLInfo(String fileName, int line, int column) {
      myColumn = column;
      myFileName = fileName;
      myLine = line;
    }

    @Override
    public void navigate(Project project) { }

    public void checkInfo(String fileName, int line, int column) {
      assertEquals(fileName, myFileName);
      assertEquals(line, myLine);
      assertEquals(column, myColumn);
    }
  }
}
