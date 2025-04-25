// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations;

import com.google.common.base.Strings;
import com.intellij.diagnostic.LoadingState;
import com.intellij.execution.*;
import com.intellij.execution.process.LocalPtyOptions;
import com.intellij.execution.process.ProcessNotCreatedException;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.platform.eel.EelApi;
import com.intellij.platform.eel.provider.EelNioBridgeService;
import com.intellij.platform.eel.provider.LocalEelDescriptor;
import com.intellij.util.EnvironmentRestorer;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FastUtilHashingStrategies;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.io.IdeUtilIoBundle;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

import static com.intellij.execution.util.ExecUtil.startProcessBlockingUsingEel;
import static com.intellij.platform.eel.provider.EelProviderUtil.getEelDescriptor;
import static com.intellij.platform.eel.provider.EelProviderUtil.upgradeBlocking;

/**
 * OS-independent way of executing external processes with complex parameters.
 * <p>
 * Main idea of the class is to accept parameters as-is, just as they should look to an external process, and quote/escape them
 * as required by the underlying platform - so to run some program with a "parameter with space" all that's needed is
 * {@code new GeneralCommandLine("some program", "parameter with space").createProcess()}.
 * <p>
 * Consider the following things when using this class.
 * <h3>Working directory</h3>
 * By default, a current directory of the IDE process is used (usually a "bin/" directory of IDE installation).
 * If child processes may create files in it, this choice is unwelcome. On the other hand, informational commands (e.g. "git --version")
 * are safe. When unsure, set it to something neutral - like a user's home or a temp directory.
 * <h3>Parent Environment</h3>
 * {@link ParentEnvironmentType Three options here}.
 * For commands designed from the ground up for typing into a terminal, use {@link ParentEnvironmentType#CONSOLE CONSOLE}
 * (typical cases: version controls, Node.js and all the surrounding stuff, Python and Ruby interpreters and utilities, etc.).
 * For GUI apps and CLI tools that aren't primarily intended to be launched by humans, use {@link ParentEnvironmentType#SYSTEM SYSTEM}
 * (examples: UI builders, browsers, XCode components). And for the empty environment, there is {@link ParentEnvironmentType#NONE NONE}.
 * According to extensive research conducted by British scientists (tm) on a diverse population of both wild and domesticated tools
 * (no one was harmed), most of them are either insensitive to the environment or fall into the first category,
 * thus backing up the choice of CONSOLE as the default value.
 * <h3>Encoding/Charset</h3>
 * The {@link #getCharset()} method is used by classes like {@link com.intellij.execution.process.OSProcessHandler OSProcessHandler}
 * or {@link com.intellij.execution.util.ExecUtil ExecUtil} to decode bytes of a child's output stream. For proper conversion,
 * the same value should be used on another side of the pipe. Chances are you don't have to mess with the setting -
 * because a platform-dependent guessing behind {@link EncodingManager#getDefaultConsoleEncoding()} is used by default and a child process
 * may happen to use a similar heuristic.
 * If the above automagic fails or more control is needed, the charset may be set explicitly. Again, remember the other side -
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
   * On macOS, a console environment is simulated (see {@link EnvironmentUtil#getEnvironmentMap()} for reasons it's needed
   * and details on how it works). On Windows and Unix hosts, this option is no different from {@code SYSTEM}
   * since there is no drastic distinction in environment between GUI and console apps.
   */
  public enum ParentEnvironmentType {NONE, SYSTEM, CONSOLE}

  private String myExePath;
  private @Nullable Path myWorkingDirectory;
  private final Map<String, String> myEnvParams = new MyMap();
  private ParentEnvironmentType myParentEnvironmentType = ParentEnvironmentType.CONSOLE;
  private final ParametersList myProgramParams = new ParametersList();
  private Charset myCharset = defaultCharset();
  private boolean myRedirectErrorStream;
  private @Nullable File myInputFile;
  private Map<Object, Object> myUserData;
  /**
   * `null` means that the ref is not initialized yet
   * `Ref(null)` means that Eel should **not** be used here
   * `Ref(not-null)` means that the Eel **should** be used
   */
  private @Nullable Ref<@Nullable EelApi> myEelApi = null;
  private @Nullable Function<ProcessBuilder, Process> myProcessCreator;

  public GeneralCommandLine() {
    this(Collections.emptyList());
  }

  public GeneralCommandLine(String @NotNull ... command) {
    this(Arrays.asList(command));
  }

  public GeneralCommandLine(@NotNull List<String> command) {
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
    myWorkingDirectory = original.myWorkingDirectory;
    myEnvParams.putAll(original.myEnvParams);
    myParentEnvironmentType = original.myParentEnvironmentType;
    original.myProgramParams.copyTo(myProgramParams);
    myCharset = original.myCharset;
    myRedirectErrorStream = original.myRedirectErrorStream;
    myInputFile = original.myInputFile;
    myUserData = null;  // user data should not be copied over
    myProcessCreator = original.myProcessCreator;
    myEelApi = original.myEelApi;
  }

  private static Charset defaultCharset() {
    return LoadingState.COMPONENTS_LOADED.isOccurred() ? EncodingManager.getInstance().getDefaultConsoleEncoding() : Charset.defaultCharset();
  }

  public @NotNull @NlsSafe String getExePath() {
    return myExePath;
  }

  public @NotNull GeneralCommandLine withExePath(@NotNull String exePath) {
    myExePath = exePath.trim();
    return this;
  }

  /** Please use {@link #withExePath}. */
  @ApiStatus.Obsolete(since = "2024.2")
  public void setExePath(@NotNull String exePath) {
    withExePath(exePath);
  }

  /**
   * Turns the command line into parameters of a new one.
   * Case in point &ndash; running a process with lower priority: {@code commandLine.withWrappingCommand("nice", "-n", "10")}.
   */
  @ApiStatus.Experimental
  public @NotNull GeneralCommandLine withWrappingCommand(@NotNull String command, @NotNull String @NotNull ... args) {
    if (myExePath == null || myExePath.isBlank()) throw new IllegalStateException("Executable is not set yet");
    myProgramParams.prepend(myExePath);
    myProgramParams.prependAll(args);
    return withExePath(command);
  }

  /** Please use {@link #getWorkingDirectory()}. */
  @ApiStatus.Obsolete(since = "2024.2")
  public File getWorkDirectory() {
    return myWorkingDirectory != null ? myWorkingDirectory.toFile() : null;
  }

  /** Please use {@link #withWorkingDirectory(Path)}. */
  @ApiStatus.Obsolete(since = "2024.2")
  public @NotNull GeneralCommandLine withWorkDirectory(@Nullable String path) {
    return withWorkingDirectory(path != null ? Path.of(path) : null);
  }

  /** Please use {@link #withWorkingDirectory(Path)}. */
  @ApiStatus.Obsolete(since = "2024.2")
  public @NotNull GeneralCommandLine withWorkDirectory(@Nullable File workDirectory) {
    return withWorkingDirectory(workDirectory != null ? workDirectory.toPath() : null);
  }

  /** Please use {@link #withWorkingDirectory(Path)}. */
  @ApiStatus.Obsolete(since = "2024.2")
  public void setWorkDirectory(@Nullable String path) {
    withWorkDirectory(path);
  }

  /** Please use {@link #withWorkingDirectory(Path)}. */
  @ApiStatus.Obsolete(since = "2024.2")
  public void setWorkDirectory(@Nullable File workDirectory) {
    withWorkDirectory(workDirectory);
  }

  public @Nullable Path getWorkingDirectory() {
    return myWorkingDirectory;
  }

  public @NotNull GeneralCommandLine withWorkingDirectory(@Nullable Path workDirectory) {
    myWorkingDirectory = workDirectory;
    return this;
  }

  /**
   * Note: the returned map is forgiving to passing {@code null} values into {@link Map#putAll}.
   */
  public @NotNull Map<String, String> getEnvironment() {
    return myEnvParams;
  }

  public @NotNull GeneralCommandLine withEnvironment(@Nullable Map<String, String> environment) {
    if (environment != null) {
      getEnvironment().putAll(environment);
    }
    return this;
  }

  public @NotNull GeneralCommandLine withEnvironment(@NotNull String key, @NotNull String value) {
    getEnvironment().put(key, value);
    return this;
  }

  public boolean isPassParentEnvironment() {
    return myParentEnvironmentType != ParentEnvironmentType.NONE;
  }

  /** @deprecated use {@link #withParentEnvironmentType(ParentEnvironmentType)} */
  @Deprecated(forRemoval = true)
  public void setPassParentEnvironment(boolean passParentEnvironment) {
    withParentEnvironmentType(passParentEnvironment ? ParentEnvironmentType.CONSOLE : ParentEnvironmentType.NONE);
  }

  public @NotNull ParentEnvironmentType getParentEnvironmentType() {
    return myParentEnvironmentType;
  }

  public @NotNull GeneralCommandLine withParentEnvironmentType(@NotNull ParentEnvironmentType type) {
    myParentEnvironmentType = type;
    return this;
  }

  /**
   * Returns an environment that will be inherited by a child process.
   * @see #getEffectiveEnvironment()
   */
  public @NotNull Map<String, String> getParentEnvironment() {
    return switch (myParentEnvironmentType) {
      case SYSTEM -> System.getenv();
      case CONSOLE -> EnvironmentUtil.getEnvironmentMap();
      default -> Collections.emptyMap();
    };
  }

  /**
   * Returns an environment as seen by a child process,
   * that is the {@link #getEnvironment() environment} merged with the {@link #getParentEnvironment() parent} one.
   */
  public @NotNull Map<String, String> getEffectiveEnvironment() {
    Map<String, String> env = new MyMap();
    setupEnvironment(env);
    return env;
  }

  public void addParameters(String @NotNull ... parameters) {
    withParameters(parameters);
  }

  public void addParameters(@NotNull List<String> parameters) {
    withParameters(parameters);
  }

  public @NotNull GeneralCommandLine withParameters(@NotNull String @NotNull ... parameters) {
    for (String parameter : parameters) addParameter(parameter);
    return this;
  }

  public @NotNull GeneralCommandLine withParameters(@NotNull List<String> parameters) {
    for (String parameter : parameters) addParameter(parameter);
    return this;
  }

  public void addParameter(@NotNull String parameter) {
    myProgramParams.add(parameter);
  }

  public @NotNull ParametersList getParametersList() {
    return myProgramParams;
  }

  public @NotNull Charset getCharset() {
    return myCharset;
  }

  public @NotNull GeneralCommandLine withCharset(@NotNull Charset charset) {
    myCharset = charset;
    return this;
  }

  /** Please use {@link #withCharset}. */
  @ApiStatus.Obsolete(since = "2024.2")
  public void setCharset(@NotNull Charset charset) {
    withCharset(charset);
  }

  public boolean isRedirectErrorStream() {
    return myRedirectErrorStream;
  }

  public @NotNull GeneralCommandLine withRedirectErrorStream(boolean redirectErrorStream) {
    myRedirectErrorStream = redirectErrorStream;
    return this;
  }

  /** Please use {@link #withRedirectErrorStream}. */
  @ApiStatus.Obsolete(since = "2024.2")
  public void setRedirectErrorStream(boolean redirectErrorStream) {
    withRedirectErrorStream(redirectErrorStream);
  }

  public @Nullable File getInputFile() {
    return myInputFile;
  }

  public @NotNull GeneralCommandLine withInput(@Nullable File file) {
    myInputFile = file;
    return this;
  }

  /**
   * Returns string representation of this command line.<br/>
   * Warning: resulting string is not OS-dependent - <b>do not</b> use it for executing this command line.
   *
   * @return single-string representation of this command line.
   */
  public @NlsSafe @NotNull String getCommandLineString() {
    return getCommandLineString(null);
  }

  /**
   * Returns string representation of this command line.<br/>
   * Warning: resulting string is not OS-dependent - <b>do not</b> use it for executing this command line.
   *
   * @param exeName use this executable name instead of given by {@link #setExePath(String)}
   * @return single-string representation of this command line.
   */
  public @NotNull String getCommandLineString(@Nullable String exeName) {
    return ParametersListUtil.join(getCommandLineList(exeName));
  }

  public @NotNull List<@NlsSafe String> getCommandLineList(@Nullable String exeName) {
    var commands = new ArrayList<@NlsSafe String>();
    commands.add(exeName != null ? exeName : myExePath != null ? myExePath : "<null>");
    commands.addAll(myProgramParams.getList());
    return commands;
  }

  /**
   * Prepares command (quotes and escapes all arguments) and returns it as a newline-separated list.
   *
   * @return command as a newline-separated list.
   * @see #getPreparedCommandLine(Platform)
   */
  public @NotNull String getPreparedCommandLine() {
    return getPreparedCommandLine(Platform.current());
  }

  /**
   * Prepares command (quotes and escapes all arguments) and returns it as a newline-separated list
   * (suitable, e.g., for passing in an environment variable).
   *
   * @param platform a target platform
   * @return command as a newline-separated list.
   */
  public @NotNull String getPreparedCommandLine(@NotNull Platform platform) {
    return String.join("\n", prepareCommandLine(myExePath != null ? myExePath : "", myProgramParams.getList(), platform));
  }

  protected @NotNull List<String> prepareCommandLine(@NotNull String command, @NotNull List<String> parameters, @NotNull Platform platform) {
    return CommandLineUtil.toCommandLine(command, parameters, platform);
  }

  @ApiStatus.NonExtendable
  public @NotNull Process createProcess() throws ExecutionException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Executing [" + getCommandLineString() + "]");
      if (myWorkingDirectory != null) {
        LOG.debug("  working dir: " + myWorkingDirectory.toAbsolutePath());
      }
      LOG.debug("  environment: " + myEnvParams + " (+" + myParentEnvironmentType + ")");
      LOG.debug("  charset: " + myCharset);
    }

    try {
      var commands = myProcessCreator != null || tryGetEel() != null
                     ? ContainerUtil.concat(List.of(myExePath), myProgramParams.getList())
                     : validateAndPrepareCommandLineForLocalRun();
      var process = startProcess(commands);
      String pidString = null;
      if (LOG.isDebugEnabled()) {
        try {
          pidString = Long.toString(process.pid());
        }
        catch (UnsupportedOperationException ignored) {
        }
        LOG.debug(String.format("Process %s started with pid %s", getCommandLineString(), pidString));
      }
      return process;
    }
    catch (IOException e) {
      if (SystemInfo.isWindows) {
        var mode = System.getProperty("jdk.lang.Process.allowAmbiguousCommands");
        @SuppressWarnings("removal") var sm = System.getSecurityManager();
        if ("false".equalsIgnoreCase(mode) || sm != null) {
          e.addSuppressed(new IllegalStateException("Suspicious state: allowAmbiguousCommands=" + mode + " SM=" + (sm != null ? sm.getClass() : null)));
        }
      }
      throw new ProcessNotCreatedException(e.getMessage(), e, this);
    }
  }

  /**
   * Allows specifying a handler for creating processes different from {@link ProcessBuilder#start()}.
   * <p>
   * Quoting, which is required for running processes locally, may be harmful for remote operating systems. F.i., arguments with spaces
   * must be quoted before passing them into {@code CreateProcess} on Windows, and must not be quoted for {@code exec} on a Unix-like OS.
   * Therefore, when the process creator is not null, various validations and mangling of arguments and environment variables are disabled.
   */
  @ApiStatus.Internal
  public final void setProcessCreator(@Nullable Function<ProcessBuilder, Process> processCreator) {
    myProcessCreator = processCreator;
  }

  @ApiStatus.Internal
  public final boolean isProcessCreatorSet() {
    return myProcessCreator != null;
  }

  /**
   * Tries to get Eel backend for this GeneralCommandLine. If this function returns {@code null}, then the old implementation should be used.
   */
  @ApiStatus.Internal
  public @Nullable EelApi tryGetEel() {
    Ref<EelApi> eelApiRef = myEelApi;
    if (eelApiRef != null) {
      return eelApiRef.get();
    }
    if (!Registry.is("ide.general.command.line.use.eel", false)) {
      myEelApi = new Ref<>(null);
      return null;
    }

    // now we need to initialize Eel here
    EelApi eelApi;
    final var exe = myExePath;
    final var workingDirectory = myWorkingDirectory;

    if (Strings.isNullOrEmpty(exe)) {
      return null;
    }

    final var exePath = Path.of(exe);

    if (ApplicationManager.getApplication().getServiceIfCreated(EelNioBridgeService.class) == null) {
      // some distributions of the IDE do not include `PlatformExtensions.xml`
      return null;
    }

    // IJPL-177172: do not use eel for absolute Windows paths (e.g., C:\...).
    // Fallback to the legacy WSL behavior where a local exe is executed in a remote working directory.
    if (SystemInfo.isWindows && OSAgnosticPathUtil.isAbsoluteDosPath(exe)) {
      eelApi = null;
    }
    else if (getEelDescriptor(exePath) != LocalEelDescriptor.INSTANCE) { // fast check
      eelApi = upgradeBlocking(getEelDescriptor(exePath));
    }
    else if (workingDirectory != null) {
      if (getEelDescriptor(workingDirectory) != LocalEelDescriptor.INSTANCE) { // also try to compute non-local EelApi from working dir
        eelApi = upgradeBlocking(getEelDescriptor(workingDirectory));
      }
      else {
        eelApi = null;
      }
    }
    else {
      eelApi = null;
    }
    myEelApi = Ref.create(eelApi);

    return eelApi;
  }

  public @NotNull ProcessBuilder toProcessBuilder() throws ExecutionException {
    return toProcessBuilder(validateAndPrepareCommandLineForLocalRun());
  }

  private List<String> validateAndPrepareCommandLineForLocalRun() throws ExecutionException {
    if (myWorkingDirectory != null && !Files.isDirectory(myWorkingDirectory)) {
      LOG.debug("Invalid working directory: " + myWorkingDirectory);
      throw new WorkingDirectoryNotFoundException(myWorkingDirectory);
    }

    if (myExePath == null || myExePath.isBlank()) {
      LOG.debug("Invalid executable: " + myExePath);
      throw new ExecutionException(IdeUtilIoBundle.message("run.configuration.error.executable.not.specified"));
    }

    for (var entry : myEnvParams.entrySet()) {
      String name = entry.getKey(), value = entry.getValue();
      if (!EnvironmentUtil.isValidName(name)) {
        throw new IllegalEnvVarException(IdeUtilIoBundle.message("run.configuration.invalid.env.name", name));
      }
      if (!EnvironmentUtil.isValidValue(value)) {
        throw new IllegalEnvVarException(IdeUtilIoBundle.message("run.configuration.invalid.env.value", name, value));
      }
    }

    String exePath = myExePath;
    if (exePath.indexOf(File.separatorChar) == -1) {
      String lookupPath = myEnvParams.get("PATH");
      if (lookupPath == null && myParentEnvironmentType == ParentEnvironmentType.CONSOLE && SystemInfo.isMac) {
        String shellPath = EnvironmentUtil.getValue("PATH");
        if (!Objects.equals(shellPath, System.getenv("PATH"))) {
          lookupPath = shellPath;
        }
      }
      if (lookupPath != null) {
        File exeFile = PathEnvironmentVariableUtil.findInPath(myExePath, lookupPath, null);
        if (exeFile != null) {
          LOG.debug(exePath + " => " + exeFile);
          exePath = exeFile.getPath();
        }
      }
    }

    return prepareCommandLine(exePath, myProgramParams.getList(), Platform.current());
  }

  /**
   * @implNote for subclasses:
   * <p>On Windows, the parameters in the {@code builder} argument must never be modified or augmented in any way.
   * Windows command line handling is extremely fragile and vague, and the exact escaping of a particular argument may vary
   * depending on the values of the preceding arguments.
   * <pre>
   *   [foo] [^] -> [foo] [^^]
   * </pre>
   * but:
   * <pre>
   *   [foo] ["] [^] -> [foo] [\"] ["^"]
   * </pre>
   * Note how the last parameter escaping changes after prepending another argument.</p>
   * <p>If you need to alter the command line passed in, override the {@link #prepareCommandLine(String, List, Platform)} method instead.</p>
   */
  protected @NotNull Process createProcess(@NotNull ProcessBuilder processBuilder) throws IOException {
    if (myProcessCreator != null) {
      return myProcessCreator.apply(processBuilder);
    }
    EelApi eelApi = tryGetEel();
    if (eelApi == null) {
      return processBuilder.start();
    }
    LocalPtyOptions ptyOptions;

    if (this instanceof PtyCommandLine ptyCommandLine) {
      ptyOptions = ptyCommandLine.getPtyOptions();
    }
    else {
      ptyOptions = null;
    }
    return startProcessBlockingUsingEel(eelApi.getExec(), processBuilder, ptyOptions, isPassParentEnvironment());
  }

  /** @deprecated please override {@link #createProcess(ProcessBuilder)} instead. */
  @Deprecated(forRemoval = true)
  protected @NotNull Process startProcess(@NotNull List<String> escapedCommands) throws IOException {
    return createProcess(toProcessBuilder(escapedCommands));
  }

  private ProcessBuilder toProcessBuilder(List<String> escapedCommands) {
    var builder = new ProcessBuilder(escapedCommands);
    setupEnvironment(builder.environment());
    if (myWorkingDirectory != null) {
      builder.directory(new File(myWorkingDirectory.toString()));
    }
    builder.redirectErrorStream(myRedirectErrorStream);
    if (myInputFile != null) {
      builder.redirectInput(ProcessBuilder.Redirect.from(myInputFile));
    }
    return builder;
  }

  protected void setupEnvironment(@NotNull Map<String, String> environment) {
    environment.clear();

    if (myParentEnvironmentType != ParentEnvironmentType.NONE && myProcessCreator == null && tryGetEel() == null) {
      environment.putAll(getParentEnvironment());
    }

    if (SystemInfo.isUnix && myProcessCreator == null && tryGetEel() == null) {
      File workDirectory = getWorkDirectory();
      if (workDirectory != null) {
        environment.put("PWD", FileUtil.toSystemDependentName(workDirectory.getAbsolutePath()));
      }
    }

    if (!myEnvParams.isEmpty()) {
      if (SystemInfo.isWindows && myProcessCreator == null && tryGetEel() == null) {
        Map<String, String> envVars = CollectionFactory.createCaseInsensitiveStringMap();
        envVars.putAll(environment);
        envVars.putAll(myEnvParams);
        environment.clear();
        environment.putAll(envVars);
      }
      else {
        environment.putAll(myEnvParams);
      }
    }

    EnvironmentRestorer.restoreOverriddenVars(environment);
    customizeEnv(environment);
  }

  /**
   * Allow plugins/modules to define a service that can modify environment variables before process execution.
   */
  private void customizeEnv(@NotNull Map<String, String> environment) {
    Application application = ApplicationManager.getApplication();
    if (application != null) {
      ExecutionEnvCustomizerService envCustomizer = application.getService(ExecutionEnvCustomizerService.class);
      if (envCustomizer != null) {
        envCustomizer.customizeEnv(this, environment);
      }
    }
  }

  /**
   * Normally, double quotes in parameters are escaped, so they arrive to a called program as-is.
   * But some commands (e.g. {@code 'cmd /c start "title" ...'}) should get their quotes non-escaped -
   * use this method to wrap such parameters (instead of using quotes).
   *
   * @see com.intellij.execution.util.ExecUtil#getTerminalCommand(String, String)
   */
  public static @NotNull String inescapableQuote(@NotNull String parameter) {
    return CommandLineUtil.specialQuote(parameter);
  }

  @Override
  public String toString() {
    return myExePath + " " + myProgramParams;
  }

  @Override
  public @Nullable <T> T getUserData(@NotNull Key<T> key) {
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

  private static final class MyMap extends Object2ObjectOpenCustomHashMap<String, String> {
    private MyMap() {
      super(FastUtilHashingStrategies.getStringStrategy(!SystemInfo.isWindows));
    }

    @Override
    public void putAll(Map<? extends String, ? extends String> map) {
      if (map != null) {
        super.putAll(map);
      }
    }
  }
}
