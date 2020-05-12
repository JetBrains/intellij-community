// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.IllegalEnvVarException;
import com.intellij.execution.Platform;
import com.intellij.execution.process.ProcessNotCreatedException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.io.IdeUtilIoBundle;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import gnu.trove.THashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;

/**
 * OS-independent way of executing external processes with complex parameters.
 * <p>
 * Main idea of the class is to accept parameters "as-is", just as they should look to an external process, and quote/escape them
 * as required by the underlying platform - so to run some program with a "parameter with space" all that's needed is
 * {@code new GeneralCommandLine("some program", "parameter with space").createProcess()}.
 * <p>
 * Consider the following things when using this class.
 *
 * <h3>Working directory</h3>
 * By default, a current directory of the IDE process is used (usually a "bin/" directory of IDE installation).
 * If a child process may create files in it, this choice is unwelcome. On the other hand, informational commands (e.g. "git --version")
 * are safe. When unsure, set it to something neutral - like user's home or a temp directory.
 *
 * <h3>Parent Environment</h3>
 * {@link ParentEnvironmentType Three options here}.
 * For commands designed from the ground up for typing into a terminal, use {@link ParentEnvironmentType#CONSOLE CONSOLE}
 * (typical cases: version controls, Node.js and all the surrounding stuff, Python and Ruby interpreters and utilities, etc).
 * For GUI apps and CLI tools that aren't primarily intended to be launched by humans, use {@link ParentEnvironmentType#SYSTEM SYSTEM}
 * (examples: UI builders, browsers, XCode components). And for the empty environment, there is {@link ParentEnvironmentType#NONE NONE}.
 * According to an extensive research conducted by British scientists (tm) on a diverse population of both wild and domesticated tools
 * (no one was harmed), most of them are either insensitive to an environment or fall into the first category,
 * thus backing up the choice of CONSOLE as the default value.
 *
 * <h3>Encoding/Charset</h3>
 * The {@link #getCharset()} method is used by classes like {@link com.intellij.execution.process.OSProcessHandler OSProcessHandler}
 * or {@link com.intellij.execution.util.ExecUtil ExecUtil} to decode bytes of a child's output stream. For proper conversion,
 * the same value should be used on another side of the pipe. Chances are you don't have to mess with the setting -
 * because a platform-dependent guessing behind {@link EncodingManager#getDefaultConsoleEncoding()} is used by default and a child process
 * may happen to use a similar heuristic.
 * If the above automagic fails or more control is needed, the charset may be set explicitly. Again, do not forget the other side -
 * call {@code addParameter("-Dfile.encoding=...")} for Java-based tools, or use {@code withEnvironment("HGENCODING", "...")}
 * for Mercurial, etc.
 *
 * @see com.intellij.execution.util.ExecUtil
 * @see com.intellij.execution.process.OSProcessHandler
 */
public class GeneralCommandLine implements UserDataHolder {
  private static final Logger LOG = Logger.getInstance(GeneralCommandLine.class);

  /**
   * Determines the scope of a parent environment passed to a child process.
   * <p>
   * {@code NONE} means a child process will receive an empty environment. <br/>
   * {@code SYSTEM} will provide it with the same environment as an IDE. <br/>
   * {@code CONSOLE} provides the child with a similar environment as if it was launched from, well, a console.
   * On OS X, a console environment is simulated (see {@link EnvironmentUtil#getEnvironmentMap()} for reasons it's needed
   * and details on how it works). On Windows and Unix hosts, this option is no different from {@code SYSTEM}
   * since there is no drastic distinction in environment between GUI and console apps.
   */
  public enum ParentEnvironmentType {NONE, SYSTEM, CONSOLE}

  private String myExePath;
  private File myWorkDirectory;
  private final Map<String, String> myEnvParams = new MyTHashMap();
  private ParentEnvironmentType myParentEnvironmentType = ParentEnvironmentType.CONSOLE;
  private final ParametersList myProgramParams = new ParametersList();
  private Charset myCharset = EncodingManager.getInstance().getDefaultConsoleEncoding();
  private boolean myRedirectErrorStream;
  private File myInputFile;
  private Map<Object, Object> myUserData;

  public GeneralCommandLine() { }

  public GeneralCommandLine(@NonNls String @NotNull ... command) {
    this(Arrays.asList(command));
  }

  public GeneralCommandLine(@NonNls @NotNull List<String> command) {
    int size = command.size();
    if (size > 0) {
      setExePath(command.get(0));
      if (size > 1) {
        addParameters(command.subList(1, size));
      }
    }
  }

  protected GeneralCommandLine(@NotNull GeneralCommandLine original) {
    myExePath = original.myExePath;
    myWorkDirectory = original.myWorkDirectory;
    myEnvParams.putAll(original.myEnvParams);
    myParentEnvironmentType = original.myParentEnvironmentType;
    original.myProgramParams.copyTo(myProgramParams);
    myCharset = original.myCharset;
    myRedirectErrorStream = original.myRedirectErrorStream;
    myInputFile = original.myInputFile;
    myUserData = null;  // user data should not be copied over
  }

  @NotNull
  public String getExePath() {
    return myExePath;
  }

  @NotNull
  public GeneralCommandLine withExePath(@NotNull String exePath) {
    myExePath = exePath.trim();
    return this;
  }

  public void setExePath(@NotNull String exePath) {
    withExePath(exePath);
  }

  public File getWorkDirectory() {
    return myWorkDirectory;
  }

  @NotNull
  public GeneralCommandLine withWorkDirectory(@Nullable String path) {
    return withWorkDirectory(path != null ? new File(path) : null);
  }

  @NotNull
  public GeneralCommandLine withWorkDirectory(@Nullable File workDirectory) {
    myWorkDirectory = workDirectory;
    return this;
  }

  public void setWorkDirectory(@Nullable String path) {
    withWorkDirectory(path);
  }

  public void setWorkDirectory(@Nullable File workDirectory) {
    withWorkDirectory(workDirectory);
  }

  /**
   * Note: the map returned is forgiving to passing null values into putAll().
   */
  @NotNull
  public Map<String, String> getEnvironment() {
    return myEnvParams;
  }

  @NotNull
  public GeneralCommandLine withEnvironment(@Nullable Map<String, String> environment) {
    if (environment != null) {
      getEnvironment().putAll(environment);
    }
    return this;
  }

  @NotNull
  public GeneralCommandLine withEnvironment(@NotNull String key, @NotNull String value) {
    getEnvironment().put(key, value);
    return this;
  }

  public boolean isPassParentEnvironment() {
    return myParentEnvironmentType != ParentEnvironmentType.NONE;
  }

  /** @deprecated use {@link #withParentEnvironmentType(ParentEnvironmentType)} */
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  @Deprecated
  public void setPassParentEnvironment(boolean passParentEnvironment) {
    withParentEnvironmentType(passParentEnvironment ? ParentEnvironmentType.CONSOLE : ParentEnvironmentType.NONE);
  }

  @NotNull
  public ParentEnvironmentType getParentEnvironmentType() {
    return myParentEnvironmentType;
  }

  @NotNull
  public GeneralCommandLine withParentEnvironmentType(@NotNull ParentEnvironmentType type) {
    myParentEnvironmentType = type;
    return this;
  }

  /**
   * Returns an environment that will be inherited by a child process.
   * @see #getEffectiveEnvironment()
   */
  @NotNull
  public Map<String, String> getParentEnvironment() {
    switch (myParentEnvironmentType) {
      case SYSTEM:
        return System.getenv();
      case CONSOLE:
        return EnvironmentUtil.getEnvironmentMap();
      default:
        return Collections.emptyMap();
    }
  }

  /**
   * Returns an environment as seen by a child process,
   * that is the {@link #getEnvironment() environment} merged with the {@link #getParentEnvironment() parent} one.
   */
  @NotNull
  public Map<String, String> getEffectiveEnvironment() {
    MyTHashMap env = new MyTHashMap();
    setupEnvironment(env);
    return env;
  }

  public void addParameters(String @NotNull ... parameters) {
    withParameters(parameters);
  }

  public void addParameters(@NotNull List<String> parameters) {
    withParameters(parameters);
  }

  @NotNull
  public GeneralCommandLine withParameters(String @NotNull ... parameters) {
    for (String parameter : parameters) addParameter(parameter);
    return this;
  }

  @NotNull
  public GeneralCommandLine withParameters(@NotNull List<String> parameters) {
    for (String parameter : parameters) addParameter(parameter);
    return this;
  }

  public void addParameter(@NotNull String parameter) {
    myProgramParams.add(parameter);
  }

  @NotNull
  public ParametersList getParametersList() {
    return myProgramParams;
  }

  @NotNull
  public Charset getCharset() {
    return myCharset;
  }

  @NotNull
  public GeneralCommandLine withCharset(@NotNull Charset charset) {
    myCharset = charset;
    return this;
  }

  public void setCharset(@NotNull Charset charset) {
    withCharset(charset);
  }

  public boolean isRedirectErrorStream() {
    return myRedirectErrorStream;
  }

  @NotNull
  public GeneralCommandLine withRedirectErrorStream(boolean redirectErrorStream) {
    myRedirectErrorStream = redirectErrorStream;
    return this;
  }

  public void setRedirectErrorStream(boolean redirectErrorStream) {
    withRedirectErrorStream(redirectErrorStream);
  }

  public File getInputFile() {
    return myInputFile;
  }

  @NotNull
  public GeneralCommandLine withInput(@Nullable File file) {
    myInputFile = file;
    return this;
  }

  /**
   * Returns string representation of this command line.<br/>
   * Warning: resulting string is not OS-dependent - <b>do not</b> use it for executing this command line.
   *
   * @return single-string representation of this command line.
   */
  @NotNull
  public String getCommandLineString() {
    return getCommandLineString(null);
  }

  /**
   * Returns string representation of this command line.<br/>
   * Warning: resulting string is not OS-dependent - <b>do not</b> use it for executing this command line.
   *
   * @param exeName use this executable name instead of given by {@link #setExePath(String)}
   * @return single-string representation of this command line.
   */
  @NotNull
  public String getCommandLineString(@Nullable String exeName) {
    return ParametersListUtil.join(getCommandLineList(exeName));
  }

  @NotNull
  public List<String> getCommandLineList(@Nullable String exeName) {
    List<String> commands = new ArrayList<>();
    if (exeName != null) {
      commands.add(exeName);
    }
    else if (myExePath != null) {
      commands.add(myExePath);
    }
    else {
      commands.add("<null>");
    }
    commands.addAll(myProgramParams.getList());
    return commands;
  }

  /**
   * Prepares command (quotes and escapes all arguments) and returns it as a newline-separated list.
   *
   * @return command as a newline-separated list.
   * @see #getPreparedCommandLine(Platform)
   */
  @NotNull
  public String getPreparedCommandLine() {
    return getPreparedCommandLine(Platform.current());
  }

  /**
   * Prepares command (quotes and escapes all arguments) and returns it as a newline-separated list
   * (suitable e.g. for passing in an environment variable).
   *
   * @param platform a target platform
   * @return command as a newline-separated list.
   */
  @NotNull
  public String getPreparedCommandLine(@NotNull Platform platform) {
    String exePath = myExePath != null ? myExePath : "";
    return StringUtil.join(prepareCommandLine(exePath, myProgramParams.getList(), platform), "\n");
  }

  @NotNull
  protected List<String> prepareCommandLine(@NotNull String command, @NotNull List<String> parameters, @NotNull Platform platform) {
    return CommandLineUtil.toCommandLine(command, parameters, platform);
  }

  @NotNull
  public Process createProcess() throws ExecutionException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Executing [" + getCommandLineString() + "]");
      LOG.debug("  environment: " + myEnvParams + " (+" + myParentEnvironmentType + ")");
      LOG.debug("  charset: " + myCharset);
    }

    try {
      if (myWorkDirectory != null) {
        if (!myWorkDirectory.exists()) {
          throw new ExecutionException(IdeUtilIoBundle.message("run.configuration.error.working.directory.does.not.exist", myWorkDirectory));
        }
        if (!myWorkDirectory.isDirectory()) {
          throw new ExecutionException(IdeUtilIoBundle.message("run.configuration.error.working.directory.not.directory", myWorkDirectory));
        }
      }

      if (StringUtil.isEmptyOrSpaces(myExePath)) {
        throw new ExecutionException(IdeUtilIoBundle.message("run.configuration.error.executable.not.specified"));
      }
    }
    catch (ExecutionException e) {
      LOG.debug(e);
      throw e;
    }

    for (Map.Entry<String, String> entry : myEnvParams.entrySet()) {
      String name = entry.getKey();
      String value = entry.getValue();
      if (!EnvironmentUtil.isValidName(name)) throw new IllegalEnvVarException(IdeUtilIoBundle.message("run.configuration.invalid.env.name", name));
      if (!EnvironmentUtil.isValidValue(value)) throw new IllegalEnvVarException(IdeUtilIoBundle.message("run.configuration.invalid.env.value", name, value));
    }

    String exePath = myExePath;
    if (SystemInfo.isMac && myParentEnvironmentType == ParentEnvironmentType.CONSOLE && exePath.indexOf(File.separatorChar) == -1) {
      String systemPath = System.getenv("PATH");
      String shellPath = EnvironmentUtil.getValue("PATH");
      if (!Objects.equals(systemPath, shellPath)) {
        File exeFile = PathEnvironmentVariableUtil.findInPath(myExePath, shellPath, null);
        if (exeFile != null) {
          LOG.debug(exePath + " => " + exeFile);
          exePath = exeFile.getPath();
        }
      }
    }

    List<String> commands = prepareCommandLine(exePath, myProgramParams.getList(), Platform.current());

    try {
      return startProcess(commands);
    }
    catch (IOException e) {
      LOG.debug(e);
      throw new ProcessNotCreatedException(e.getMessage(), e, this);
    }
  }

  /**
   * @implNote for subclasses:
   * <p>On Windows the escapedCommands argument must never be modified or augmented in any way.
   * Windows command line handling is extremely fragile and vague, and the exact escaping of a particular argument may vary
   * depending on values of the preceding arguments.
   * <pre>
   *   [foo] [^] -> [foo] [^^]
   * </pre>
   * but:
   * <pre>
   *   [foo] ["] [^] -> [foo] [\"] ["^"]
   * </pre>
   * Notice how the last parameter escaping changes after prepending another argument.</p>
   * <p>If you need to alter the command line passed in, override the {@link #prepareCommandLine(String, List, Platform)} method instead.</p>
   */
  @NotNull
  protected Process startProcess(@NotNull List<String> escapedCommands) throws IOException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Building process with commands: " + escapedCommands);
    }
    ProcessBuilder builder = new ProcessBuilder(escapedCommands);
    setupEnvironment(builder.environment());
    builder.directory(myWorkDirectory);
    builder.redirectErrorStream(myRedirectErrorStream);
    if (myInputFile != null) {
      builder.redirectInput(ProcessBuilder.Redirect.from(myInputFile));
    }
    return buildProcess(builder).start();
  }

  /**
   * Executed with pre-filled ProcessBuilder as the param and
   * gives the last chance to configure starting process
   * parameters before a process is started
   * @param builder filed ProcessBuilder
   */
  @NotNull
  protected ProcessBuilder buildProcess(@NotNull ProcessBuilder builder) {
    return builder;
  }

  protected void setupEnvironment(@NotNull Map<String, String> environment) {
    environment.clear();

    if (myParentEnvironmentType != ParentEnvironmentType.NONE) {
      environment.putAll(getParentEnvironment());
    }

    if (SystemInfo.isUnix) {
      File workDirectory = getWorkDirectory();
      if (workDirectory != null) {
        environment.put("PWD", FileUtil.toSystemDependentName(workDirectory.getAbsolutePath()));
      }
    }

    if (!myEnvParams.isEmpty()) {
      if (SystemInfo.isWindows) {
        THashMap<String, String> envVars = new THashMap<>(CaseInsensitiveStringHashingStrategy.INSTANCE);
        envVars.putAll(environment);
        envVars.putAll(myEnvParams);
        environment.clear();
        environment.putAll(envVars);
      }
      else {
        environment.putAll(myEnvParams);
      }
    }
  }

  /**
   * Normally, double quotes in parameters are escaped, so they arrive to a called program as-is.
   * But some commands (e.g. {@code 'cmd /c start "title" ...'}) should get their quotes non-escaped.
   * Wrapping a parameter by this method (instead of using quotes) will do exactly this.
   *
   * @see com.intellij.execution.util.ExecUtil#getTerminalCommand(String, String)
   */
  @NotNull
  public static String inescapableQuote(@NotNull String parameter) {
    return CommandLineUtil.specialQuote(parameter);
  }

  @Override
  public String toString() {
    return myExePath + " " + myProgramParams;
  }

  @Nullable
  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    if (myUserData == null) return null;
    @SuppressWarnings("unchecked") T t = (T)myUserData.get(key);
    return t;
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    if (myUserData == null) {
      if (value == null) return;
      myUserData = new HashMap<>();
    }
    myUserData.put(key, value);
  }

  private static class MyTHashMap extends THashMap<String, String> {
    private MyTHashMap() {
      super(SystemInfo.isWindows ? CaseInsensitiveStringHashingStrategy.INSTANCE : ContainerUtil.canonicalStrategy());
    }

    @Override
    public void putAll(Map<? extends String, ? extends String> map) {
      if (map != null) {
        super.putAll(map);
      }
    }
  }
}