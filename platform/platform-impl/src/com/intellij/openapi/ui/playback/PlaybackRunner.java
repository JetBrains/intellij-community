// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.playback;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.UiActivityMonitor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.playback.commands.*;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.text.StringTokenizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class PlaybackRunner {
  private static final Logger LOG = Logger.getInstance(PlaybackRunner.class);

  private Robot myRobot;

  private final String myScript;
  private final StatusCallback myCallback;

  private final ArrayList<PlaybackCommand> myCommands = new ArrayList<>();
  private ActionCallback myActionCallback;
  private boolean myStopRequested;

  private final boolean myUseDirectActionCall;
  private final boolean myUseTypingTargets;

  private File myScriptDir;
  private final boolean myStopOnAppDeactivation;
  private final ApplicationActivationListener myAppListener;

  private final HashSet<Class<?>> myFacadeClasses = new HashSet<>();
  private final ArrayList<StageInfo> myCurrentStageDepth = new ArrayList<>();
  private final ArrayList<StageInfo> myPassedStages = new ArrayList<>();

  private long myContextTimestamp;

  private final Map<String, String> myRegistryValues = new HashMap<>();

  protected final Disposable myOnStop = Disposer.newDisposable();

  public PlaybackRunner(String script,
                        StatusCallback callback,
                        final boolean useDirectActionCall,
                        boolean stopOnAppDeactivation,
                        boolean useTypingTargets) {
    myScript = script;
    myCallback = callback;
    myUseDirectActionCall = useDirectActionCall;
    myUseTypingTargets = useTypingTargets;
    myStopOnAppDeactivation = stopOnAppDeactivation;
    myAppListener = new ApplicationActivationListener() {
      @Override
      public void applicationDeactivated(@NotNull IdeFrame ideFrame) {
        if (myStopOnAppDeactivation) {
          myCallback.message(null, "App lost focus, stopping...", StatusCallback.Type.message);
          stop();
        }
      }
    };
  }

  public ActionCallback run() {
    myStopRequested = false;

    myRegistryValues.clear();
    final UiActivityMonitor activityMonitor = UiActivityMonitor.getInstance();
    activityMonitor.clear();
    activityMonitor.setActive(true);
    myCurrentStageDepth.clear();
    myPassedStages.clear();
    myContextTimestamp++;

    subscribeListeners(ApplicationManager.getApplication().getMessageBus().connect(myOnStop));
    Disposer.register(myOnStop, () -> {
      onStop();
    });

    try {
      myActionCallback = new ActionCallback();
      myActionCallback.doWhenProcessed(() -> {
        Disposer.dispose(myOnStop);

        SwingUtilities.invokeLater(() -> {
          activityMonitor.setActive(false);
          restoreRegistryValues();
        });
      });

      if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
        myRobot = new Robot();
      }

      parse();

      new Thread("playback runner") {
        @Override
        public void run() {
          if (myUseDirectActionCall) {
            executeFrom(0, getScriptDir());
          }
          else {
            IdeEventQueue.getInstance().doWhenReady(() -> executeFrom(0, getScriptDir()));
          }
        }
      }.start();
    }
    catch (AWTException e) {
      LOG.error(e);
    }

    return myActionCallback;
  }

  private void restoreRegistryValues() {
    final Set<String> storedKeys = myRegistryValues.keySet();
    for (String each : storedKeys) {
      Registry.get(each).setValue(myRegistryValues.get(each));
    }
  }

  private void executeFrom(final int cmdIndex, File baseDir) {
    if (cmdIndex < myCommands.size()) {
      final PlaybackCommand cmd = myCommands.get(cmdIndex);
      if (myStopRequested) {
        myCallback.message(null, "Stopped", StatusCallback.Type.message);
        myActionCallback.setRejected();
        return;
      }
      @SuppressWarnings("unchecked")
      Set<Class<?>> facadeClassesClone = (Set<Class<?>>)myFacadeClasses.clone();
      PlaybackContext context =
        new PlaybackContext(this, myCallback, cmdIndex, myRobot, myUseDirectActionCall, myUseTypingTargets, cmd, baseDir,
                            facadeClassesClone) {
          private final long myTimeStamp = myContextTimestamp;

          @Override
          public void pushStage(StageInfo info) {
            myCurrentStageDepth.add(info);
          }

          @Override
          public StageInfo popStage() {
            if (myCurrentStageDepth.size() > 0) {
              return myCurrentStageDepth.remove(myCurrentStageDepth.size() - 1);
            }

            return null;
          }

          @Override
          public int getCurrentStageDepth() {
            return myCurrentStageDepth.size();
          }

          @Override
          public void addPassed(StageInfo stage) {
            myPassedStages.add(stage);
          }

          @Override
          public boolean isDisposed() {
            return myTimeStamp != myContextTimestamp;
          }

          @Override
          public void storeRegistryValue(String key) {
            if (!myRegistryValues.containsKey(key)) {
              myRegistryValues.put(key, Registry.stringValue(key));
            }
          }
        };
      final Promise<Object> cmdCallback = cmd.execute(context);
      cmdCallback
        .onSuccess(it -> {
          if (cmd.canGoFurther()) {
            executeFrom(cmdIndex + 1, context.getBaseDir());
          }
          else {
            myCallback.message(null, "Stopped: cannot go further", StatusCallback.Type.message);
            myActionCallback.setDone();
          }
        })
        .onError(error -> {
          myCallback.message(null, "Stopped: " + error, StatusCallback.Type.message);
          LOG.warn("Callback step stopped with error: " + error, error);
          myActionCallback.setRejected();
        });
    }
    else {
      myCallback.message(null, "Finished OK " + myPassedStages.size() + " tests", StatusCallback.Type.message);
      myActionCallback.setDone();
    }
  }

  protected void subscribeListeners(MessageBusConnection connection) {
    connection.subscribe(ApplicationActivationListener.TOPIC, myAppListener);
  }

  protected void onStop() {
    myCommands.clear();
  }

  private void parse() {
    includeScript(myScript, getScriptDir(), myCommands, 0);
  }

  private void includeScript(String scriptText, File scriptDir, ArrayList<? super PlaybackCommand> commandList, int line) {
    final StringTokenizer tokens = new StringTokenizer(scriptText, "\n");
    while (tokens.hasMoreTokens()) {
      final String eachLine = tokens.nextToken();

      String includeCmd = AbstractCommand.CMD_PREFIX + "include";
      String importCallCmd = AbstractCommand.CMD_PREFIX + "importCall";

      if (eachLine.startsWith(includeCmd)) {
        File file = new PathMacro().setScriptDir(scriptDir).resolveFile(eachLine.substring(includeCmd.length()).trim(), scriptDir);
        if (!file.exists()) {
          commandList.add(new ErrorCommand("Cannot find file to include: " + file.getAbsolutePath(), line));
          return;
        }
        try {
          String include = FileUtil.loadFile(file);
          myCommands.add(new PrintCommand(eachLine, line));
          includeScript(include, file.getParentFile(), commandList, 0);
        }
        catch (IOException e) {
          commandList.add(new ErrorCommand("Error reading file: " + file.getAbsolutePath(), line));
          return;
        }
      }
      else if (eachLine.startsWith(importCallCmd)) {
        String className = eachLine.substring(importCallCmd.length()).trim();
        try {
          Class<?> facadeClass = Class.forName(className);
          myFacadeClasses.add(facadeClass);
          myCommands.add(new PrintCommand(eachLine, line++));
        }
        catch (ClassNotFoundException e) {
          commandList.add(new ErrorCommand("Cannot find class: " + className, line));
          return;
        }
      }
      else {
        final PlaybackCommand cmd = createCommand(eachLine, line++, scriptDir);
        commandList.add(cmd);
      }
    }
  }

  protected PlaybackCommand createCommand(String string, int line, File scriptDir) {
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
    else {
      cmd = new AlphaNumericTypeCommand(string, line);
    }

    cmd.setScriptDir(scriptDir);

    return cmd;
  }

  public void stop() {
    myStopRequested = true;
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

      public abstract void messageEdt(@Nullable PlaybackContext context, String text, Type type);
    }
  }
}
