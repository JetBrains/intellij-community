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
import com.intellij.util.text.StringTokenizer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class PlaybackRunner {

  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.debugger.extensions.PlaybackRunner");

  private Robot myRobot;

  private final String myScript;
  private final StatusCallback myCallback;

  private final ArrayList<PlaybackCommand> myCommands = new ArrayList<>();
  private ActionCallback myActionCallback;
  private boolean myStopRequested;

  private final boolean myUseDirectActionCall;
  private boolean myUseTypingTargets;

  private File myScriptDir;
  private boolean myStopOnAppDeactivation;
  private final ApplicationActivationListener myAppListener;

  private HashSet<Class> myFacadeClasses = new HashSet<>();
  private ArrayList<StageInfo> myCurrentStageDepth = new ArrayList<>();
  private ArrayList<StageInfo> myPassedStages = new ArrayList<>();

  private long myContextTimestamp;

  private Map<String, String> myRegistryValues = new HashMap<>();

  private Disposable myOnStop = Disposer.newDisposable();

  public PlaybackRunner(String script, StatusCallback callback, final boolean useDirectActionCall, boolean stopOnAppDeactivation, boolean useTypingTargets) {
    myScript = script;
    myCallback = callback;
    myUseDirectActionCall = useDirectActionCall;
    myUseTypingTargets = useTypingTargets;
    myStopOnAppDeactivation = stopOnAppDeactivation;
    myAppListener = new ApplicationActivationListener.Adapter() {
      @Override
      public void applicationDeactivated(IdeFrame ideFrame) {
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

    ApplicationManager.getApplication().getMessageBus().connect(ApplicationManager.getApplication()).subscribe(ApplicationActivationListener.TOPIC, myAppListener);

    try {
      myActionCallback = new ActionCallback();
      myActionCallback.doWhenProcessed(() -> {
        stop();

        SwingUtilities.invokeLater(() -> {
          activityMonitor.setActive(false);
          restoreRegistryValues();
        });
      });

      myRobot = new Robot();

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
      final PlaybackContext context =
        new PlaybackContext(this, myCallback, cmdIndex, myRobot, myUseDirectActionCall, myUseTypingTargets, cmd, baseDir, (Set<Class>)myFacadeClasses.clone()) {

          private long myTimeStamp = myContextTimestamp;

          public void pushStage(StageInfo info) {
            myCurrentStageDepth.add(info);
          }

          public StageInfo popStage() {
            if (myCurrentStageDepth.size() > 0) {
              return myCurrentStageDepth.remove(myCurrentStageDepth.size() - 1);
            }

            return null;
          }

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
      final ActionCallback cmdCallback = cmd.execute(context);
      cmdCallback.doWhenDone(() -> {
        if (cmd.canGoFurther()) {
          executeFrom(cmdIndex + 1, context.getBaseDir());
        }
        else {
          myCallback.message(null, "Stopped", StatusCallback.Type.message);
          myActionCallback.setDone();
        }
      }).doWhenRejected(() -> {
        myCallback.message(null, "Stopped", StatusCallback.Type.message);
        myActionCallback.setRejected();
      });
    }
    else {
      myCallback.message(null, "Finished OK " + myPassedStages.size() + " tests", StatusCallback.Type.message);
      myActionCallback.setDone();
    }
  }

  private void parse() {
    includeScript(myScript, getScriptDir(), myCommands, 0);
  }

  private void includeScript(String scriptText, File scriptDir, ArrayList<PlaybackCommand> commandList, int line) {
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
    } else if (string.startsWith(AbstractCommand.CMD_PREFIX + AbstractCommand.CMD_PREFIX)) {
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

  private void setDone() {
    myActionCallback.setDone();
  }

  public void stop() {
    myStopRequested = true;
    Disposer.dispose(myOnStop);
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
