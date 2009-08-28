/*
 * User: anna
 * Date: 22-May-2008
 */
package com.intellij.coverage;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.process.DefaultJavaProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import com.sun.tools.javac.Main;
import junit.framework.Assert;
import junit.framework.TestCase;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class IdeaCoverageInstrumenterTest extends TestCase {
  private File myDataFile;
  private File myClassFile;

  @Override
  protected void tearDown() throws Exception {
    FileUtil.delete(myDataFile);
    FileUtil.delete(myClassFile);
    super.tearDown();
  }

  public void testSimple() throws Exception {
    doTest("Simple");
  }

  public void testStaticFieldInInterface() throws Exception {
    doTest("staticFieldInInterface");
  }

  public void testNotExpressions() throws Exception {
    doTest("notExpressions");
  }

  public void testBranches() throws Exception {
    doTest("branches");
  }

  private void doTest(final String className) throws Exception {
    final String testDataPath = PathManagerEx.getTestDataPath() + File.separatorChar + "coverage" + File.separatorChar + className + File.separator;

    Main.compile(new String[]{testDataPath + "Test.java"});

    myDataFile = new File(testDataPath + "Test.ic");
    myClassFile = new File(testDataPath + "Test.class");

    final GeneralCommandLine cmd = new GeneralCommandLine();
    cmd.setExePath(System.getenv("JAVA_HOME") + File.separator + "bin" + File.separator + "java");
    final IDEACoverageRunner coverageRunner = new IDEACoverageRunner();
    final JavaParameters javaParameters = new JavaParameters();
    coverageRunner.appendCoverageArgument(myDataFile.getPath(), new String[]{"Test"}, javaParameters, false, false);
    cmd.addParameter(javaParameters.getVMParametersList().getArray()[0]);
    cmd.addParameter("-classpath");
    cmd.addParameter(PathManager.getLibPath() + File.separator + "coverage-agent.jar;"+
                     PathManagerEx.getTestDataPath() + File.separatorChar + "coverage" + File.separatorChar + className);
    cmd.addParameter("Test");

    OSProcessHandler handler = new DefaultJavaProcessHandler(cmd);

    handler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(final ProcessEvent event, final Key outputType) {
        System.out.println(event.getText());
      }
    });
    handler.startNotify();
    handler.waitFor();
    handler.destroyProcess();

    final ProjectData projectInfo = coverageRunner.loadCoverageData(myDataFile);
    assert projectInfo != null;

    final StringBuffer buf = new StringBuffer();

    final ClassData classInfo = projectInfo.getClassData("Test");

    assert classInfo != null;

    final Object[] objects = classInfo.getLines();
    final ArrayList<LineData> lines = new ArrayList<LineData>();
    for (Object object : objects) {
      lines.add((LineData)object);
    }
    Collections.sort(lines, new Comparator<LineData>() {
      public int compare(final LineData l1, final LineData l2) {
        return l1.getLineNumber() - l2.getLineNumber();
      }
    });
    for (LineData info : lines) {
      buf.append(info.getLineNumber()).append(":").append(info.getStatus() == 0 ? "NONE" : info.getStatus() == 1 ? "PARTIAL" : "FULL").append("\n");
    }

    final String expected = StringUtil.convertLineSeparators(String.valueOf(FileUtil.loadFileText(new File(testDataPath + "expected.txt"))));
    Assert.assertEquals(expected, buf.toString());
  }

}