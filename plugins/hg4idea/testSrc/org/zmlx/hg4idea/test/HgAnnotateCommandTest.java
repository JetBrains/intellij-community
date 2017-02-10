/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.zmlx.hg4idea.test;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.thoughtworks.xstream.XStream;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.command.HgAnnotateCommand;
import org.zmlx.hg4idea.provider.annotate.HgAnnotationLine;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

/**
 * @author Kirill Likhodedov
 */
public class HgAnnotateCommandTest extends HgSingleUserTest {

  private File myPluginRoot;
  private File myAnnotateDataDir;
  private File myOutputsDir;
  private List<HgAnnotationLine> myAnnotations;

  @BeforeClass
  private void loadEthalonAnnotations() throws IOException {
    myPluginRoot = new File(PluginPathManager.getPluginHomePath(HgVcs.VCS_NAME));
    myAnnotateDataDir = new File(myPluginRoot, "testData/annotate");
    myOutputsDir = new File(myAnnotateDataDir, "outputs");

    final File etalonFile = new File(myAnnotateDataDir, "etalon");

    XStream xStream = new XStream();
    FileReader reader = new FileReader(etalonFile);
    try {
      myAnnotations = (List<HgAnnotationLine>) xStream.fromXML(reader);
    }
    finally {
      reader.close();
    }
  }
  
  @DataProvider(name = "annotate_output")
  public Object[][] createValidData() throws IOException {
    Object[][] data = new Object[myOutputsDir.listFiles().length][];
    for (int i = 0; i < myOutputsDir.listFiles().length; i++) {
      File annotateData = myOutputsDir.listFiles()[i];
      String fileText = FileUtil.loadFile(annotateData);
      data[i] = new String[] {annotateData.getName(), fileText};
    }
    return data;
  }
  
  @Test(dataProvider = "annotate_output")
  public void testParse(String fileName, String annotationNativeOutput)
    throws NoSuchMethodException, InvocationTargetException, IllegalAccessException
  {
    HgAnnotateCommand command = new HgAnnotateCommand(myProject);
    Method parseMethod = HgAnnotateCommand.class.getDeclaredMethod("parse", List.class);
    parseMethod.setAccessible(true);

    List<String> annotations = Arrays.asList(annotationNativeOutput.split("(\n|\r|\r\n)"));
    List<HgAnnotationLine> result = (List<HgAnnotationLine>)parseMethod.invoke(command, annotations);
    assertEquals(result, myAnnotations);
  }

  //@Test
  public void generateCorrectAnnotation() throws IOException {
    final File etalonFile = new File(myAnnotateDataDir, "etalon");

    File outputFile = new File(myOutputsDir, "hg_1.9.0");
    String output = FileUtil.loadFile(outputFile);
    String[] split = output.split("(\n|\r|\r\n)");
    List<HgAnnotationLine> annotationLines = new ArrayList<>(split.length);
    
    Pattern pattern = Pattern.compile("\\s*(.+)\\s+(\\d+)\\s+([a-fA-F0-9]+)\\s+(\\d{4}-\\d{2}-\\d{2}):\\s*(\\d+): ?(.*)");
    for (String line : split) {
      Matcher matcher = pattern.matcher(line);
      if (!matcher.matches()) {
        fail("Couldn't parse line [ " + line + " ]");
      }
      String user = matcher.group(1);
      String shortRev = matcher.group(2);
      String fullRev = matcher.group(3);
      String date = matcher.group(4);
      String lineNum = matcher.group(5);
      String content = matcher.group(6);
      annotationLines
        .add(new HgAnnotationLine(user, HgRevisionNumber.getInstance(shortRev, fullRev), date, Integer.parseInt(lineNum), content));
    }

    XStream xStream = new XStream();
    FileWriter writer = new FileWriter(etalonFile);
    try {
      xStream.toXML(annotationLines, writer);
    }
    finally {
      writer.close();
    }
  }


}
