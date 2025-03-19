// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.playback;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.UiActivityMonitor;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.commands.*;
import com.intellij.openapi.util.CheckedDisposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.concurrency.EdtScheduler;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.text.StringTokenizer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;

public class PlaybackRunner {
  private PlaybackCommandReporter commandStartStopProcessor = PlaybackCommandReporter.EMPTY_PLAYBACK_COMMAND_REPORTER;

  private static final Logger LOG = Logger.getInstance(PlaybackRunner.class);

  private Robot myRobot;

  private final String myScript;
  private final StatusCallback callback;

  private final List<CommandDescriptor> commands = new ArrayList<>();
  private CompletableFuture<?> actionCallback;
  private boolean isStopRequested;

  private final boolean useDirectActionCall;
  private final boolean useTypingTargets;

  private File myScriptDir;
  private final boolean stopOnAppDeactivation;
  private final ApplicationActivationListener appListener;

  private final HashSet<Class<?>> facadeClasses = new HashSet<>();
  private final ArrayList<StageInfo> currentStageDepth = new ArrayList<>();
  private final ArrayList<StageInfo> passedStages = new ArrayList<>();

  private long myContextTimestamp;

  private final Map<String, String> registryValues = new HashMap<>();

  protected final CheckedDisposable onStop = Disposer.newCheckedDisposable();

  public PlaybackRunner(String script,
                        StatusCallback callback,
                        final boolean useDirectActionCall,
                        boolean stopOnAppDeactivation,
                        boolean useTypingTargets) {
    myScript = script;
    this.callback = callback;
    this.useDirectActionCall = useDirectActionCall;
    this.useTypingTargets = useTypingTargets;
    this.stopOnAppDeactivation = stopOnAppDeactivation;
    appListener = new ApplicationActivationListener() {
      @Override
      public void applicationDeactivated(@NotNull IdeFrame ideFrame) {
        if (PlaybackRunner.this.stopOnAppDeactivation) {
          PlaybackRunner.this.callback.message(null, "App lost focus, stopping...", StatusCallback.Type.message);
          stop();
        }
      }
    };
  }

  public void runBlocking(long timeoutMs) throws ExecutionException, InterruptedException, TimeoutException {
    if (timeoutMs > 0) {
      run().get(timeoutMs, TimeUnit.MILLISECONDS);
    } else {
      run().get();
    }
  }

  public CompletableFuture<?> run() {
    commandStartStopProcessor.startOfScript(getProject());
    isStopRequested = false;

    registryValues.clear();
    UiActivityMonitor activityMonitor = UiActivityMonitor.getInstance();
    activityMonitor.clear();
    activityMonitor.setActive(true);
    currentStageDepth.clear();
    passedStages.clear();
    myContextTimestamp++;

    subscribeListeners(ApplicationManager.getApplication().getMessageBus().connect(onStop));
    Disposer.register(onStop, () -> {
      commandStartStopProcessor.endOfScript(getProject());
      onStop();
    });

    actionCallback = new CompletableFuture<>();
    actionCallback.handle((o, throwable) -> {
      try {
        if (throwable != null) {
          commandStartStopProcessor.scriptCanceled();
        }
      }
      finally {
        Disposer.dispose(onStop);

        SwingUtilities.invokeLater(() -> {
          activityMonitor.setActive(false);
          restoreRegistryValues();
        });
      }
      return null;
    });

    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      try {
        myRobot = new Robot();
      }
      catch (AWTException e) {
        LOG.info(e);
      }
    }

    try {
      commands.addAll(includeScript(myScript, getScriptDir()));
    }
    catch (Exception e) {
      String message = "Failed to parse script commands: " + myScript;
      LOG.error(message, e);
      actionCallback.completeExceptionally(new RuntimeException(message, e));
      return actionCallback;
    }

    new Thread(null, Context.current().wrap(() -> {
      if (useDirectActionCall) {
        executeFrom(0, getScriptDir());
      }
      else {
        IdeEventQueue.getInstance().doWhenReady(Context.current().wrap(() -> executeFrom(0, getScriptDir())));
      }
    }), "playback runner").start();

    return actionCallback;
  }

  private void restoreRegistryValues() {
    Set<String> storedKeys = registryValues.keySet();
    for (String each : storedKeys) {
      Registry.get(each).setValue(registryValues.get(each));
    }
  }

  private void executeFrom(final int commandIndex, File baseDir) {
    if (commandIndex >= commands.size()) {
      callback.message(null, "Finished OK " + passedStages.size() + " tests", StatusCallback.Type.message);
      actionCallback.complete(null);
      return;
    }

    CommandDescriptor commandDescriptor = commands.get(commandIndex);

    PlaybackCommand command = createCommand(commandDescriptor.fullLine, commandDescriptor.line, commandDescriptor.scriptDir);
    if (isStopRequested || command == null) {
      callback.message(null, "Stopped", StatusCallback.Type.message);
      actionCallback.cancel(false);
      return;
    }

    @SuppressWarnings("unchecked")
    Set<Class<?>> facadeClassesClone = (Set<Class<?>>)facadeClasses.clone();
    PlaybackContext context = new PlaybackContext(this, callback, commandIndex, myRobot, useDirectActionCall, useTypingTargets, command,
                                                  baseDir, facadeClassesClone) {
      private final long myTimeStamp = myContextTimestamp;

      @Override
      public void pushStage(StageInfo info) {
        currentStageDepth.add(info);
      }

      @Override
      public StageInfo popStage() {
        if (!currentStageDepth.isEmpty()) {
          return currentStageDepth.remove(currentStageDepth.size() - 1);
        }

        return null;
      }

      @Override
      public int getCurrentStageDepth() {
        return currentStageDepth.size();
      }

      @Override
      public void addPassed(StageInfo stage) {
        passedStages.add(stage);
      }

      @Override
      public boolean isDisposed() {
        return myTimeStamp != myContextTimestamp || isStopRequested;
      }

      @Override
      public void storeRegistryValue(String key) {
        if (!registryValues.containsKey(key)) {
          registryValues.put(key, Registry.stringValue(key));
        }
      }

      @Override
      public void setProject(@Nullable Project project) {
        myRunner.setProject(project);
      }

      @Override
      public @NotNull Project getProject() {
        Project project = myRunner.getProject();
        if (project == null) {
          throw new IllegalStateException(
            "Project is null. Use a project-aware runner and check if its project has been set up properly");
        }
        return project;
      }
    };

    commandStartStopProcessor.startOfCommand(commandDescriptor.fullLine);

    CompletableFuture<?> commandFuture = command.execute(context);
    Context initialContext = Context.current();
    commandFuture.whenComplete((result, error) -> {
      if (error != null) {
        commandStartStopProcessor.endOfCommand(error.getMessage());
        callback.message(null, "Stopped: " + error, StatusCallback.Type.message);
        LOG.warn("Callback step stopped with error: " + error, error);
        actionCallback.completeExceptionally(error);
        return;
      }

      commandStartStopProcessor.endOfCommand(null);
      try (Scope ignored = initialContext.makeCurrent()) {
        if (command.canGoFurther()) {
          int delay = getDelay(command);
          if (delay > 0) {
            if (SwingUtilities.isEventDispatchThread()) {
              EdtScheduler.getInstance().schedule(delay, Context.current().wrap(() -> {
                if (!onStop.isDisposed()) {
                  executeFrom(commandIndex + 1, context.getBaseDir());
                }
              }));
            }
            else {
              LockSupport.parkUntil(System.currentTimeMillis() + delay);
              executeFrom(commandIndex + 1, context.getBaseDir());
            }
          }
          else {
            executeFrom(commandIndex + 1, context.getBaseDir());
          }
        }
        else {
          callback.message(null, "Stopped: cannot go further", StatusCallback.Type.message);
          actionCallback.complete(null);
        }
      }
    });
  }

  public int getDelay(@NotNull PlaybackCommand command) {
    //noinspection SpellCheckingInspection
    return command instanceof TypeCommand ? Registry.intValue("actionSystem.playback.typecommand.delay") : 0;
  }

  protected void setProject(@Nullable Project project) {
  }

  protected @Nullable Project getProject() {
    return null;
  }

  protected void subscribeListeners(MessageBusConnection connection) {
    connection.subscribe(ApplicationActivationListener.TOPIC, appListener);
  }

  public PlaybackRunner setCommandStartStopProcessor(@NotNull PlaybackCommandReporter commandStartStopProcessor) {
    this.commandStartStopProcessor = commandStartStopProcessor;
    return this;
  }

  protected void onStop() {
    commands.clear();
  }

  static final String INCLUDE_CMD = AbstractCommand.CMD_PREFIX + "include";
  static final String IMPORT_CALL_CMD = AbstractCommand.CMD_PREFIX + "importCall";

  private @NotNull List<CommandDescriptor> includeScript(String scriptText, File scriptDir) {
    List<CommandDescriptor> commands = new ArrayList<>();
    final StringTokenizer tokens = new StringTokenizer(scriptText, "\n");
    int line = 0;
    while (tokens.hasMoreTokens()) {
      final String eachLine = tokens.nextToken();

      if (eachLine.startsWith(INCLUDE_CMD)) {
        File file = new PathMacro().setScriptDir(scriptDir).resolveFile(eachLine.substring(INCLUDE_CMD.length()).trim(), scriptDir);
        if (!file.exists()) {
          throw new RuntimeException("Cannot find file to include at line " + line + ": " + file.getAbsolutePath());
        }
        try {
          String include = FileUtil.loadFile(file, true);
          commands.add(new CommandDescriptor(PrintCommand.PREFIX + " " + eachLine, line, scriptDir));
          List<CommandDescriptor> includeCommands = includeScript(include, file.getParentFile());
          commands.addAll(includeCommands);
        }
        catch (IOException e) {
          throw new RuntimeException("Error reading file at line " + line + ": " + file.getAbsolutePath());
        }
      }
      else if (eachLine.startsWith(IMPORT_CALL_CMD)) {
        String className = eachLine.substring(IMPORT_CALL_CMD.length()).trim();
        try {
          Class<?> facadeClass = Class.forName(className);
          facadeClasses.add(facadeClass);
          commands.add(new CommandDescriptor(PrintCommand.PREFIX + " " + eachLine, line++, scriptDir));
        }
        catch (ClassNotFoundException e) {
          throw new RuntimeException("Cannot find class at line " + line + ": " + className);
        }
      }
      else {
        commands.add(new CommandDescriptor(eachLine, line++, scriptDir));
      }
    }
    return commands;
  }

  /**
   * This data class aggregates parameters of a command to be called.
   * We do not create instances of commands beforehand because
   * command classes may be provided by plugins and may prevent plugin from unloading [IDEA-259898].
   */
  private record CommandDescriptor(String fullLine, int line, File scriptDir) {
  }

  protected @Nullable PlaybackCommand createCommand(String string, int line, File scriptDir) {
    AbstractCommand cmd;

    if (string.startsWith(RegistryValueCommand.PREFIX)) {
      cmd = new RegistryValueCommand(string, line);
    }
    else if (string.startsWith(AbstractCommand.CMD_PREFIX + AbstractCommand.CMD_PREFIX)) {
      cmd = new EmptyCommand(line);
    }
    else if (string.startsWith(KeyCodeTypeCommand.PREFIX)) {
      cmd = new KeyCodeTypeCommand(string, line);
    }
    else if (string.startsWith(DelayCommand.PREFIX)) {
      cmd = new DelayCommand(string, line);
    }
    else if (string.startsWith(KeyShortcutCommand.PREFIX)) {
      cmd = new KeyShortcutCommand(string, line);
    }
    else if (string.startsWith(ActionCommand.PREFIX)) {
      cmd = new ActionCommand(string, line);
    }
    else if (string.startsWith(ToggleActionCommand.PREFIX)) {
      cmd = new ToggleActionCommand(string, line);
    }
    else if (string.startsWith(StopCommand.PREFIX)) {
      cmd = new StopCommand(string, line);
    }
    else if (string.startsWith(AssertFocused.PREFIX)) {
      return new AssertFocused(string, line);
    }
    else if (string.startsWith(CallCommand.PREFIX)) {
      cmd = new CallCommand(string, line);
    }
    else if (string.startsWith(CdCommand.PREFIX)) {
      cmd = new CdCommand(string, line);
    }
    else if (string.startsWith(PushStage.PREFIX)) {
      cmd = new PushStage(string, line);
    }
    else if (string.startsWith(PopStage.PREFIX)) {
      cmd = new PopStage(string, line);
    }
    else if (string.startsWith(PrintCommand.PREFIX)) {
      cmd = new PrintCommand(string.substring(PrintCommand.PREFIX.length() + 1), line);
    }
    else {
      if (string.startsWith(AbstractCommand.CMD_PREFIX)) {
        cmd = null;
        LOG.error("Command " + string + " is not found");
      }
      else {
        cmd = new AlphaNumericTypeCommand(string, line);
      }
    }
    if (cmd != null) {
      cmd.setScriptDir(scriptDir);
    }

    return cmd;
  }

  public void stop() {
    isStopRequested = true;
  }

  public File getScriptDir() {
    return myScriptDir != null ? myScriptDir : new File(System.getProperty("user.dir"));
  }

  public void setScriptDir(File baseDir) {
    myScriptDir = baseDir;
  }

  public interface StatusCallback {
    enum Type {message, error, code, test}

    void message(@Nullable PlaybackContext context, String text, Type type);

    abstract class Edt implements StatusCallback {
      @Override
      public final void message(final PlaybackContext context,
                                final String text,
                                final Type type) {
        if (SwingUtilities.isEventDispatchThread()) {
          messageEdt(context, text, type);
        }
        else {
          SwingUtilities.invokeLater(() -> messageEdt(context, text, type));
        }
      }

      public abstract void messageEdt(@Nullable PlaybackContext context, @NlsContexts.StatusBarText String text, Type type);
    }
  }
}
