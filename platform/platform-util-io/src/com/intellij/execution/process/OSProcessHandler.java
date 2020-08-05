// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.diagnostic.LoadingState;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.util.DeprecatedMethodException;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.BaseDataReader;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;

public class OSProcessHandler extends BaseOSProcessHandler {
  private static final Logger LOG = Logger.getInstance(OSProcessHandler.class);
  private static final Set<String> REPORTED_EXECUTIONS = ContainerUtil.newConcurrentSet();
  private static final long ALLOWED_TIMEOUT_THRESHOLD = 10;

  private static final Key<Set<File>> DELETE_FILES_ON_TERMINATION = Key.create("OSProcessHandler.FileToDelete");

  private final boolean myHasErrorStream;
  @NotNull
  private final ModalityState myModality;
  private boolean myHasPty;
  private boolean myDestroyRecursively = true;
  private final Set<? extends File> myFilesToDelete;

  public OSProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    super(startProcess(commandLine), commandLine.getCommandLineString(), commandLine.getCharset());

    LoadingState.CONFIGURATION_STORE_INITIALIZED.checkOccurred();

    setHasPty(isPtyProcess(getProcess()));
    myHasErrorStream = !commandLine.isRedirectErrorStream();
    myFilesToDelete = commandLine.getUserData(DELETE_FILES_ON_TERMINATION);
    myModality = getDefaultModality();
  }

  @NotNull
  public static ModalityState getDefaultModality() {
    Application app = ApplicationManager.getApplication();
    return app == null ? ModalityState.NON_MODAL : app.getDefaultModalityState();
  }

  /** @deprecated use {@link #OSProcessHandler(Process, String)} (or any other constructor) */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public OSProcessHandler(@NotNull Process process) {
    this(process, null);
    DeprecatedMethodException.report("Use OSProcessHandler(Process, String) instead");
  }

  /**
   * {@code commandLine} must not be empty (for correct thread attribution in the stacktrace)
   */
  public OSProcessHandler(@NotNull Process process, /*@NotNull*/ String commandLine) {
    this(process, commandLine, EncodingManager.getInstance().getDefaultConsoleEncoding());
  }

  /**
   * {@code commandLine} must not be empty (for correct thread attribution in the stacktrace)
   */
  public OSProcessHandler(@NotNull Process process, /*@NotNull*/ String commandLine, @Nullable Charset charset) {
    this(process, commandLine, charset, null);
  }

  /**
   * {@code commandLine} must not be empty (for correct thread attribution in the stacktrace)
   */
  public OSProcessHandler(@NotNull Process process, /*@NotNull*/ String commandLine, @Nullable Charset charset, @Nullable Set<? extends File> filesToDelete) {
    super(process, commandLine, charset);
    setHasPty(isPtyProcess(process));
    myFilesToDelete = filesToDelete;
    myHasErrorStream = true;
    myModality = getDefaultModality();
  }

  @NotNull
  private static Process startProcess(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    try {
      return commandLine.createProcess();
    }
    catch (Throwable e) {
      deleteTempFiles(commandLine.getUserData(DELETE_FILES_ON_TERMINATION));
      throw e;
    }
  }

  @Override
  public boolean waitFor() {
    checkEdtAndReadAction(this);
    return super.waitFor();
  }

  @Override
  public boolean waitFor(long timeoutInMilliseconds) {
    if (timeoutInMilliseconds > ALLOWED_TIMEOUT_THRESHOLD) {
      checkEdtAndReadAction(this);
    }
    return super.waitFor(timeoutInMilliseconds);
  }

  /**
   * Checks if we are going to wait for {@code processHandler} to finish on EDT or under ReadAction. Logs error if we do so.
   * <br/><br/>
   * HOW-TO fix an error from this method:
   * <ul>
   * <li>You are on the pooled thread under {@link com.intellij.openapi.application.ReadAction ReadAction}:
   * <ul>
   *     <li>Synchronous (you need to return execution result or derived information to the caller) - get rid the ReadAction or synchronicity.
   *    *     Move execution part out of the code executed under ReadAction, or make your execution asynchronous - execute on
   *    *     {@link Task.Backgroundable other thread} and invoke a callback.</li>
   *     <li>Non-synchronous (you don't need to return something) - execute on another thread. E.g. using {@link Task.Backgroundable}</li>
   * </ul>
   * </li>
   *
   * <li>You are on EDT:
   * <ul>
   *
   * <li>Outside of {@link com.intellij.openapi.application.WriteAction WriteAction}:
   *   <ul>
   *     <li>Synchronous (you need to return execution result or derived information to the caller) - execute under
   *       {@link ProgressManager#runProcessWithProgressSynchronously(Runnable, String, boolean, com.intellij.openapi.project.Project) modal progress}.</li>
   *     <li>Non-synchronous (you don't need to return something) - execute on the pooled thread. E.g. using {@link Task.Backgroundable}</li>
   *   </ul>
   * </li>
   *
   * <li>Under {@link com.intellij.openapi.application.WriteAction WriteAction}
   *   <ul>
   *     <li>Synchronous (you need to return execution result or derived information to the caller) - get rid the WriteAction or synchronicity.
   *       Move execution part out of the code executed under WriteAction, or make your execution asynchronous - execute on
   *      {@link Task.Backgroundable other thread} and invoke a callback.</li>
   *     <li>Non-synchronous (you don't need to return something) - execute on the pooled thread. E.g. using {@link Task.Backgroundable}</li>
   *   </ul>
   * </li>
   * </ul></li></ul>
   *
   * @apiNote works only in the internal non-headless mode. Reports once per running session per stacktrace per cause.
   */
  public static void checkEdtAndReadAction(@NotNull ProcessHandler processHandler) {
    Application application = ApplicationManager.getApplication();
    if (application == null || !application.isInternal() || application.isHeadlessEnvironment()) {
      return;
    }
    String message = null;
    if (application.isDispatchThread()) {
      message = "Synchronous execution on EDT: ";
    }
    else if (application.isReadAccessAllowed()) {
      message = "Synchronous execution under ReadAction: ";
    }
    if (message != null && REPORTED_EXECUTIONS.add(ExceptionUtil.currentStackTrace())) {
      LOG.error(message + processHandler + ", see com.intellij.execution.process.OSProcessHandler#checkEdtAndReadAction() Javadoc for resolutions");
    }
  }

  private static void deleteTempFiles(Set<? extends File> tempFiles) {
    if (tempFiles != null) {
      try {
        for (File file : tempFiles) {
          FileUtil.delete(file);
        }
      }
      catch (Throwable t) {
        LOG.error("failed to delete temp. files", t);
      }
    }
  }

  private static boolean isPtyProcess(Process process) {
    Class<?> c = process.getClass();
    while (c != null) {
      if ("com.pty4j.unix.UnixPtyProcess".equals(c.getName()) || "com.pty4j.windows.WinPtyProcess".equals(c.getName())) {
        return true;
      }
      c = c.getSuperclass();
    }
    return false;
  }

  @Override
  protected void onOSProcessTerminated(int exitCode) {
    if (myModality != ModalityState.NON_MODAL) {
      ProgressManager.getInstance().runProcess(() -> super.onOSProcessTerminated(exitCode), new EmptyProgressIndicator(myModality));
    } else {
      super.onOSProcessTerminated(exitCode);
    }
    deleteTempFiles(myFilesToDelete);
  }

  @Override
  protected boolean processHasSeparateErrorStream() {
    return myHasErrorStream;
  }

  protected boolean shouldDestroyProcessRecursively() {
    // Override this method if you want to kill process recursively (whole process try) by default
    // such behaviour is better than default java one, which doesn't kill children processes
    return myDestroyRecursively;
  }

  public void setShouldDestroyProcessRecursively(boolean destroyRecursively) {
    myDestroyRecursively = destroyRecursively;
  }

  @Override
  protected void doDestroyProcess() {
    // Override this method if you want to customize default destroy behaviour, e.g.
    // if you want use some soft-kill.
    final Process process = getProcess();
    if (shouldDestroyProcessRecursively() && processCanBeKilledByOS(process)) {
      killProcessTree(process);
    }
    else {
      process.destroy();
    }
  }

  public static boolean processCanBeKilledByOS(Process process) {
    return !(process instanceof SelfKiller);
  }

  /**
   * Kills the whole process tree asynchronously.
   * As a potentially time-consuming operation, it's executed asynchronously on a pooled thread.
   *
   * @param process Process
   */
  protected void killProcessTree(@NotNull final Process process) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      killProcessTreeSync(process);
    }
    else {
      executeTask(() -> killProcessTreeSync(process));
    }
  }

  private void killProcessTreeSync(@NotNull Process process) {
    LOG.debug("killing process tree");
    final boolean destroyed = OSProcessUtil.killProcessTree(process);
    if (!destroyed) {
      if (!process.isAlive()) {
        LOG.warn("Process has been already terminated: " + myCommandLine);
      }
      else {
        LOG.warn("Cannot kill process tree. Trying to destroy process using Java API. Cmdline:\n" + myCommandLine);
        process.destroy();
      }
    }
  }

  /**
   * <p>In case of PTY this process handler will use blocking read because {@link InputStream#available()} doesn't work for Pty4j, and there
   * is no reason to "disconnect" leaving PTY alive. See {@link BaseDataReader.SleepingPolicy} for more info.</p>
   *
   * <p>The value should be set before {@link #startNotify()} invocation.
   * It is set by default in case of using GeneralCommandLine based constructor.</p>
   *
   * @param hasPty {@code true} if process is PTY-based.
   */
  public void setHasPty(boolean hasPty) {
    myHasPty = hasPty;
  }

  /**
   * Rule of thumb: use {@link BaseOutputReader.Options#BLOCKING} for short-living process that you never want to "disconnect" from.
   * See {@link BaseDataReader.SleepingPolicy} for the whole story.
   */
  @NotNull
  @Override
  protected BaseOutputReader.Options readerOptions() {
    return myHasPty ? BaseOutputReader.Options.BLOCKING : super.readerOptions();  // blocking read in case of PTY-based process
  }

  /**
   * Registers a file to delete after the given command line finishes.
   * In order to have an effect, the command line has to be executed with {@link #OSProcessHandler(GeneralCommandLine)}.
   */
  public static void deleteFileOnTermination(@NotNull GeneralCommandLine commandLine, @NotNull File fileToDelete) {
    Set<File> set = commandLine.getUserData(DELETE_FILES_ON_TERMINATION);
    if (set == null) {
      set = new HashSet<>();
      commandLine.putUserData(DELETE_FILES_ON_TERMINATION, set);
    }
    set.add(fileToDelete);
  }

  public static class Silent extends OSProcessHandler {
    public Silent(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
      super(commandLine);
    }

    @NotNull
    @Override
    protected BaseOutputReader.Options readerOptions() {
      return BaseOutputReader.Options.forMostlySilentProcess();
    }
  }
}