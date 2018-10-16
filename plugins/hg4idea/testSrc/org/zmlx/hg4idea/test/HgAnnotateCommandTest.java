// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.test;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.LineTokenizer;
import com.thoughtworks.xstream.XStream;
import org.junit.Before;
import org.junit.Test;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.command.HgAnnotateCommand;
import org.zmlx.hg4idea.provider.annotate.HgAnnotationLine;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
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

  @Before
  public void loadEthalonAnnotations() throws IOException {
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

  @Test
  public void testParse() throws Exception {
    for (File file : myOutputsDir.listFiles()) {
      String annotationNativeOutput = FileUtil.loadFile(file);

      HgAnnotateCommand command = new HgAnnotateCommand(myProject);
      Method parseMethod = HgAnnotateCommand.class.getDeclaredMethod("parse", List.class);
      parseMethod.setAccessible(true);

      List<String> annotations = Arrays.asList(LineTokenizer.tokenize(annotationNativeOutput, false));
      List<HgAnnotationLine> result = (List<HgAnnotationLine>)parseMethod.invoke(command, annotations);
      assertEquals(result, myAnnotations, file.getName());
    }
  }

  //@Test
  public void generateCorrectAnnotation() throws IOException {
    final File etalonFile = new File(myAnnotateDataDir, "etalon");

    File outputFile = new File(myOutputsDir, "hg_1.9.0");
    String output = FileUtil.loadFile(outputFile);
    String[] split = LineTokenizer.tokenize(output, false);
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
