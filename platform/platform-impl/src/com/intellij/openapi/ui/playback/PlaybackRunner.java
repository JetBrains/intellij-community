/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.ui.playback.commands.AssertFocused;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.playback.commands.*;
import com.intellij.openapi.ui.playback.commands.ActionCommand;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.text.StringTokenizer;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class PlaybackRunner {

  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.debugger.extensions.PlaybackRunner");

  private Robot myRobot;

  private final String myScript;
  private final StatusCallback myCallback;

  private final ArrayList<PlaybackCommand> myCommands = new ArrayList<PlaybackCommand>();
  private ActionCallback myActionCallback;
  private boolean myStopRequested;
  private final boolean myUseDirectActionCall;
  private File myBaseDir;

  public PlaybackRunner(String script, StatusCallback callback, final boolean useDirectActionCall) {
    myScript = script;
    myCallback = callback;
    myUseDirectActionCall = useDirectActionCall;
  }

  public ActionCallback run() {
    myStopRequested = false;

    try {
      myActionCallback = new ActionCallback();

      myRobot = new Robot();

      parse();

      new Thread() {
        @Override
        public void run() {
          if (myUseDirectActionCall) {
            executeFrom(0);
          } else {
            IdeEventQueue.getInstance().doWhenReady(new Runnable() {
              public void run() {
                executeFrom(0);
              }
            });
          }
        }
      }.start();

    }
    catch (AWTException e) {
      LOG.error(e);
    }

    return myActionCallback;
  }

  private void executeFrom(final int cmdIndex) {
    if (cmdIndex < myCommands.size()) {
      final PlaybackCommand cmd = myCommands.get(cmdIndex);
      if (myStopRequested) {
        myCallback.message("Stopped", cmdIndex);
        myActionCallback.setRejected();
        return;
      }
      final ActionCallback cmdCallback = cmd.execute(new PlaybackContext(myCallback, cmdIndex, myRobot, myUseDirectActionCall, cmd));
      cmdCallback.doWhenDone(new Runnable() {
        public void run() {
          if (cmd.canGoFurther()) {
            executeFrom(cmdIndex + 1);
          }
          else {
            myActionCallback.setDone();
          }
        }
      }).doWhenRejected(new Runnable() {
        public void run() {
          myCallback.message("Stopped", cmdIndex);
          myActionCallback.setRejected();
        }
      });
    }
    else {
      myCallback.message("Finished", myCommands.size() - 1);
      myActionCallback.setDone();
    }
  }

  private void parse() {
    includeScript(myScript, myCommands, 0);
  }

  private void includeScript(String scriptText, ArrayList<PlaybackCommand> commandList, int line) {
    final StringTokenizer tokens = new StringTokenizer(scriptText, "\n");
    while (tokens.hasMoreTokens()) {
      final String eachLine = tokens.nextToken();

      String cdCmd = AbstractCommand.CMD_PREFIX + "cd";
      String includeCmd = AbstractCommand.CMD_PREFIX + "include";

      if (eachLine.startsWith(includeCmd)) {
        File file = PlaybackCallFacade.getFile(getBaseDir(), eachLine.substring(includeCmd.length()).trim());
        if (!file.exists()) {
          commandList.add(new ErrorCommand("Cannot find file to include: " + file.getAbsolutePath(), line));
          return;
        }
        try {
          String include = FileUtil.loadFile(file);
          myCommands.add(new PrintCommand(eachLine, line));
          includeScript(include, commandList, 0);
        }
        catch (IOException e) {
          commandList.add(new ErrorCommand("Error reading file: " + file.getAbsolutePath(), line));
          return;
        }
      } else if (eachLine.startsWith(cdCmd)) {
        File dir = new File(eachLine.substring(cdCmd.length()).trim());
        if (!dir.exists()) {
          commandList.add(new ErrorCommand("Cannot cd to: " + dir.getPath(), line));
          return;
        }
        
        if (dir.isAbsolute()) {
          myBaseDir = dir;
          commandList.add(new PrintCommand("Base dir set to: " + myBaseDir.getAbsolutePath(), line++));
        } else {
          dir = new File(getBaseDir(), dir.getPath());
          if (!dir.exists()) {
            commandList.add(new ErrorCommand("Cannot cd to: " + dir.getAbsolutePath(), line));
            return;
          } else {
            myBaseDir = dir;
            commandList.add(new PrintCommand("Base dir set to: " + myBaseDir.getAbsolutePath(), line++));
          }
        }
        
      } else {
        final PlaybackCommand cmd = createCommand(eachLine, line++);
        commandList.add(cmd);
      }
    }
  }

  private PlaybackCommand createCommand(String string, int line) {
    AbstractCommand cmd;
    String actualString = string.toLowerCase();

    if (actualString.startsWith(AbstractCommand.CMD_PREFIX + AbstractCommand.CMD_PREFIX)) {
      cmd = new EmptyCommand(line);
    } else if (actualString.startsWith(KeyCodeTypeCommand.PREFIX)) {
      cmd = new KeyCodeTypeCommand(string, line);
    } else if (actualString.startsWith(DelayCommand.PREFIX)) {
      cmd =  new DelayCommand(string, line);
    } else if (actualString.startsWith(KeyShortcutCommand.PREFIX)) {
      cmd = new KeyShortcutCommand(string, line);
    } else if (actualString.startsWith(ActionCommand.PREFIX)) {
      return new ActionCommand(string, line);
    } else if (actualString.startsWith(StopCommand.PREFIX)) {
      return new StopCommand(string, line);
    } else if (actualString.startsWith(AssertFocused.PREFIX)) {
      return new AssertFocused(string, line);
    } else if (actualString.startsWith(CallCommand.PREFIX)) {
      return new CallCommand(string, line);
    } else {
      cmd = new AlphaNumericTypeCommand(string, line);
    }

    cmd.setBaseDir(getBaseDir());
    
    return cmd;
  }

  private void setDone() {
    myActionCallback.setDone();
  }

  public void stop() {
    myStopRequested = true;
  }

  public File getBaseDir() {
    return myBaseDir != null ? myBaseDir : new File(System.getProperty("user.dir"));
  }

  public interface StatusCallback {
    void error(String text, int currentLine);

    void message(String text, int currentLine);

    public abstract static class Edt implements StatusCallback {
      public final void error(final String text, final int currentLine) {
        if (SwingUtilities.isEventDispatchThread()) {
          errorEdt(text, currentLine);
        } else {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              errorEdt(text, currentLine);
            }
          });
        }
      }

      public abstract void errorEdt(String text, int curentLine);

      public final void message(final String text, final int currentLine) {
        if (SwingUtilities.isEventDispatchThread()) {
          messageEdt(text, currentLine);
        } else {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              messageEdt(text, currentLine);
            }
          });
        }
      }

      public abstract void messageEdt(String text, int curentLine);
    }
  }


}
