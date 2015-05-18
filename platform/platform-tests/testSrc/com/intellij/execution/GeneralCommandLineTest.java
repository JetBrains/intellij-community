/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

import static com.intellij.openapi.util.Pair.pair;
import static com.intellij.util.containers.ContainerUtil.newHashMap;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class GeneralCommandLineTest {

  private static final String[] ARGUMENTS = {
    "with space",
    "\"quoted\"",
    "\"quoted with spaces\"",
    "",
    "  ",
    "param 1",
    "\"",
    "quote\"inside",
    "space \"and \"quotes\" inside",
    "\"space \"and \"quotes\" inside\"",
    "param2",
    "trailing slash\\",
     // "two trailing slashes\\\\" /* doesn't work on Windows*/
  };

  @Test
  public void printCommandLine() {
    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath("e x e path");
    commandLine.addParameter("with space");
    commandLine.addParameter("\"quoted\"");
    commandLine.addParameter("\"quoted with spaces\"");
    commandLine.addParameters("param 1", "param2");
    commandLine.addParameter("trailing slash\\");
    assertEquals("\"e x e path\"" +
                 " \"with space\"" +
                 " \\\"quoted\\\"" +
                 " \"\\\"quoted with spaces\\\"\"" +
                 " \"param 1\"" +
                 " param2" +
                 " \"trailing slash\\\"",
                 commandLine.getCommandLineString());
  }

  @Test
  public void unicodePath() throws Exception {
    String mark = String.valueOf(new Random().nextInt());
    File script;
    if (SystemInfo.isWindows) {
      script = ExecUtil.createTempExecutableScript(
        "path with spaces 'and quotes' и юникодом ", ".cmd",
        "@echo " + mark + "\n"
      );
    }
    else {
      script = ExecUtil.createTempExecutableScript(
        "path with spaces 'and quotes' и юникодом ", ".sh",
        "#!/bin/sh\n" + "echo " + mark + "\n"
      );
    }

    try {
      GeneralCommandLine commandLine = new GeneralCommandLine(script.getPath());
      String output = execAndGetOutput(commandLine, null);
      assertEquals(mark + "\n", StringUtil.convertLineSeparators(output));
    }
    finally {
      FileUtil.delete(script);
    }
  }

  @Test
  public void unicodeClassPath() throws Exception {
    assumeTrue(SystemInfo.isUnix);

    File dir = FileUtil.createTempDirectory("path with spaces 'and quotes' и юникодом ", ".tmp");
    try {
      GeneralCommandLine commandLine = makeJavaCommand(ParamPassingTest.class, dir);
      commandLine.addParameter("test");
      String output = execAndGetOutput(commandLine, null);
      assertEquals("test\n", StringUtil.convertLineSeparators(output));
    }
    finally {
      FileUtil.delete(dir);
    }
  }

  @Test
  public void testPassingArgumentsToJavaApp() throws Exception {
    GeneralCommandLine commandLine = makeJavaCommand(ParamPassingTest.class, null);
    String[] args = ArrayUtil.mergeArrays(ARGUMENTS, "&<>()@^|", "\"&<>()@^|\"");
    commandLine.addParameters(args);
    String output = execAndGetOutput(commandLine, null);
    assertParamPassingTestOutput(output, args);
  }

  @Test
  public void testPassingArgumentsToJavaAppThroughWinShell() throws Exception {
    assumeTrue(SystemInfo.isWindows);
    // passing "^" argument doesn't work for cmd.exe
    String[] args = ARGUMENTS;
    GeneralCommandLine commandLine = makeJavaCommand(ParamPassingTest.class, null);
    String oldExePath = commandLine.getExePath();
    commandLine.setExePath("cmd.exe");
    // the test will fails if "call" is omitted
    commandLine.getParametersList().prependAll("/D", "/C", "call", oldExePath);
    commandLine.addParameters(args);
    String output = execAndGetOutput(commandLine, null);
    assertParamPassingTestOutput(output, args);
  }

  @Test
  public void testPassingArgumentsToJavaAppThroughCmdScriptAndWinShell() throws Exception {
    assumeTrue(SystemInfo.isWindows);
    // passing "^" argument doesn't work for cmd.exe
    String[] args = ARGUMENTS;
    File cmdScript = createCmdFileLaunchingJavaApp();
    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath("cmd.exe");
    // the test will fails if "call" is omitted
    commandLine.addParameters("/D", "/C", "call", cmdScript.getAbsolutePath());
    commandLine.addParameters(args);
    String output = execAndGetOutput(commandLine, null);
    assertParamPassingTestOutput(output, args);
  }

  @NotNull
  private File createCmdFileLaunchingJavaApp() throws Exception {
    File cmdScript = FileUtil.createTempFile(new File(PathManager.getTempPath(), "My Program Files" /* path with spaces */),
                                             "my-script", ".cmd", true, true);
    GeneralCommandLine commandLine = makeJavaCommand(ParamPassingTest.class, null);
    FileUtil.writeToFile(cmdScript, "@" + commandLine.getCommandLineString() + " %*");
    if (!cmdScript.setExecutable(true, true)) {
      throw new ExecutionException("Failed to make temp file executable: " + cmdScript);
    }
    return cmdScript;
  }

  private static void assertParamPassingTestOutput(@NotNull String actualOutput, @NotNull String... expectedOutputParameters) {
    String content = StringUtil.join(expectedOutputParameters, "\n");
    if (expectedOutputParameters.length > 0) {
      content += "\n";
    }
    assertEquals(content, StringUtil.convertLineSeparators(actualOutput));
  }

  @Test
  public void unicodeArguments() throws Exception {
    assumeTrue("UTF-8".equals(System.getProperty("file.encoding")));

    File script;
    GeneralCommandLine commandLine;
    String encoding = null;
    if (SystemInfo.isWindows) {
      script = ExecUtil.createTempExecutableScript(
        "args.", ".js",
        "WSH.Echo(\"=====\");\n" +
        "for (i = 0; i < WSH.Arguments.length; i++) {\n" +
        "  WSH.Echo(WSH.Arguments(i));\n" +
        "}\n" +
        "WSH.Echo(\"=====\");\n"
      );
      commandLine = new GeneralCommandLine("cscript", "//Nologo", "//U", script.getPath());
      encoding = "UTF-16LE";
    }
    else {
      script = ExecUtil.createTempExecutableScript(
        "args.", ".sh",
        "#!/bin/sh\n\n" +
        "echo \"=====\"\n" +
        "for f in \"$@\" ; do echo \"$f\"; done\n" +
        "echo \"=====\"\n"
      );
      commandLine = new GeneralCommandLine(script.getPath());
    }

    try {
      commandLine.addParameters("немного", "юникодных", "параметров");
      String output = execAndGetOutput(commandLine, encoding);
      assertEquals("=====\nнемного\nюникодных\nпараметров\n=====\n", StringUtil.convertLineSeparators(output));
    }
    finally {
      FileUtil.delete(script);
    }
  }

  @Test
  public void winShellCommand() throws Exception {
    assumeTrue(SystemInfo.isWindows);

    String string = "http://localhost/wtf?a=b&c=d";
    String echo = ExecUtil.execAndReadLine(new GeneralCommandLine(ExecUtil.getWindowsShellName(), "/c", "echo", string));
    assertEquals('"' + string + '"', echo);
  }

  @Test
  public void winShellScriptQuoting() throws Exception {
    assumeTrue(SystemInfo.isWindows);
    String scriptPrefix = "my_script";
    for (String cmdScriptExt : new String[] {".cmd", ".bat"}) {
      File script = ExecUtil.createTempExecutableScript(
        scriptPrefix, cmdScriptExt,
        "@echo %1\n"
      );
      String param = "a&b";
      GeneralCommandLine commandLine = new GeneralCommandLine(script.getAbsolutePath(), param);
      String text = commandLine.getPreparedCommandLine(Platform.WINDOWS);
      assertEquals(commandLine.getExePath() + "\n" + StringUtil.wrapWithDoubleQuote(param), text);
      try {
        String output = execAndGetOutput(commandLine, null);
        assertEquals(StringUtil.wrapWithDoubleQuote(param), output.trim());
      }
      finally {
        FileUtil.delete(script);
      }
    }
  }

  @Test
  public void winShellQuotingWithExtraSwitch() throws Exception {
    assumeTrue(SystemInfo.isWindows);
    String param = "a&b";
    GeneralCommandLine commandLine = new GeneralCommandLine("cmd", "/D", "/C", "echo", param);
    String output = execAndGetOutput(commandLine, null);
    assertEquals(StringUtil.wrapWithDoubleQuote(param), output.trim());
  }

  @Test
  public void hackyEnvMap () throws Exception {
    Map<String, String> env = new GeneralCommandLine().getEnvironment();

    //noinspection ConstantConditions
    env.putAll(null);

    try {
      env.put("key1", null);
      fail("null values should be rejected");
    }
    catch (AssertionError ignored) { }

    try {
      Map<String, String> indirect = newHashMap(pair("key2", (String)null));
      env.putAll(indirect);
      fail("null values should be rejected");
    }
    catch (AssertionError ignored) { }
  }

  @Test
  public void environmentPassing() throws Exception {
    Map<String, String> testEnv = new HashMap<String, String>();
    testEnv.put("VALUE_1", "some value");
    testEnv.put("VALUE_2", "another\n\"value\"");

    GeneralCommandLine commandLine = makeJavaCommand(EnvPassingTest.class, null);
    checkEnvPassing(commandLine, testEnv, true);
    checkEnvPassing(commandLine, testEnv, false);
  }

  @Test
  public void unicodeEnvironment() throws Exception {
    assumeTrue("UTF-8".equals(System.getProperty("file.encoding")));

    Map<String, String> testEnv = new HashMap<String, String>();
    testEnv.put("VALUE_1", "немного");
    testEnv.put("VALUE_2", "юникода");

    GeneralCommandLine commandLine = makeJavaCommand(EnvPassingTest.class, null);
    checkEnvPassing(commandLine, testEnv, true);
    checkEnvPassing(commandLine, testEnv, false);
  }

  @Test
  public void emptyEnvironmentPassing() throws Exception {
    Map<String, String> env = newHashMap(pair("a", "b"), pair("", "c"));
    Map<String, String> expected = newHashMap(pair("a", "b"));
    GeneralCommandLine commandLine = makeJavaCommand(EnvPassingTest.class, null);
    checkEnvPassing(commandLine, env, expected, false);
  }

  private static String execAndGetOutput(GeneralCommandLine commandLine, @Nullable String encoding) throws Exception {
    Process process = commandLine.createProcess();
    String stdOut = loadTextFromStream(process.getInputStream(), encoding);
    String stdErr = loadTextFromStream(process.getErrorStream(), encoding);
    int result = process.waitFor();
    assertEquals("Command:\n" + commandLine.getCommandLineString()
                 + "\nStandard output:\n" + stdOut
                 + "\nStandard error:\n" + stdErr,
                 0, result);
    return stdOut;
  }

  private static String loadTextFromStream(@NotNull InputStream stream, @Nullable String encoding) throws IOException {
    byte[] bytes = FileUtil.loadBytes(stream);
    return encoding != null ? new String(bytes, encoding) : new String(bytes);
  }

  private GeneralCommandLine makeJavaCommand(Class<?> testClass, @Nullable File copyTo) throws IOException, URISyntaxException {
    String className = testClass.getName();
    URL url = getClass().getClassLoader().getResource(className.replace(".", "/") + ".class");
    assertNotNull(url);

    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(System.getProperty("java.home") + (SystemInfo.isWindows ? "\\bin\\java.exe" : "/bin/java"));

    String encoding = System.getProperty("file.encoding");
    if (encoding != null) {
      commandLine.addParameter("-D" + "file.encoding=" + encoding);
    }

    commandLine.addParameter("-cp");
    String[] packages = className.split("\\.");
    File classFile = new File(url.toURI());
    if (copyTo == null) {
      File dir = classFile;
      for (String ignored : packages) dir = dir.getParentFile();
      commandLine.addParameter(dir.getPath());
    }
    else {
      File dir = copyTo;
      for (int i = 0; i < packages.length - 1; i++) dir = new File(dir, packages[i]);
      FileUtil.copy(classFile, new File(dir, classFile.getName()));
      commandLine.addParameter(copyTo.getPath());
    }

    commandLine.addParameter(className);
    commandLine.setRedirectErrorStream(true);

    return commandLine;
  }

  private static void checkEnvPassing(GeneralCommandLine commandLine, Map<String, String> testEnv, boolean passParentEnv) throws Exception {
    checkEnvPassing(commandLine, testEnv, testEnv, passParentEnv);
  }

  private static void checkEnvPassing(GeneralCommandLine commandLine,
                                      Map<String, String> testEnv,
                                      Map<String, String> expectedOutputEnv,
                                      boolean passParentEnv) throws Exception {
    commandLine.getEnvironment().putAll(testEnv);
    commandLine.setPassParentEnvironment(passParentEnv);
    String output = execAndGetOutput(commandLine, null);

    Set<String> lines = new HashSet<String>(Arrays.asList(StringUtil.convertLineSeparators(output).split("\n")));
    lines.remove("=====");

    for (Map.Entry<String, String> entry : expectedOutputEnv.entrySet()) {
      String str = EnvPassingTest.format(entry);
      assertTrue("\"" + str + "\" should be in " + lines,
                 lines.contains(str));
    }

    Map<String, String> parentEnv = System.getenv();
    List<String> missed = new ArrayList<String>();
    for (Map.Entry<String, String> entry : parentEnv.entrySet()) {
      String str = EnvPassingTest.format(entry);
      if (!lines.contains(str)) {
        missed.add(str);
      }
    }

    long pctMissed = Math.round((100.0 * missed.size()) / parentEnv.size());
    if (passParentEnv && pctMissed > 25 || !passParentEnv && pctMissed < 75) {
      fail("% missed: " + pctMissed + ", missed: " + missed + ", passed: " + lines);
    }
  }
}
