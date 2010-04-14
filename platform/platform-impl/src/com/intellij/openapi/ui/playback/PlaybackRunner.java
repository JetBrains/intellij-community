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
import com.intellij.util.text.StringTokenizer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
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
      cmd.execute(myCallback, myRobot, myUseDirectActionCall).doWhenDone(new Runnable() {
        public void run() {
          if (cmd.canGoFurther()) {
            executeFrom(cmdIndex + 1);
          } else {
            myActionCallback.setDone();
          }
        }
      }).doWhenRejected(new Runnable() {
        public void run() {
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
    final StringTokenizer tokens = new StringTokenizer(myScript, "\n");
    int line = 0;
    while (tokens.hasMoreTokens()) {
      final String eachLine = tokens.nextToken();
      final PlaybackCommand cmd = createCommand(eachLine, line++);
      myCommands.add(cmd);
    }
  }

  private PlaybackCommand createCommand(String string, int line) {
    String actualString = string.toLowerCase();

    if (actualString.startsWith(AbstractCommand.CMD_PREFIX + AbstractCommand.CMD_PREFIX)) {
      return new EmptyCommand(line);
    }

    if (actualString.startsWith(KeyCodeTypeCommand.PREFIX)) {
      return new KeyCodeTypeCommand(string, line);
    }

    if (actualString.startsWith(DelayCommand.PREFIX)) {
      return new DelayCommand(string, line);
    }

    if (actualString.startsWith(KeyShortcutCommand.PREFIX)) {
      return new KeyShortcutCommand(string, line);
    }

    if (actualString.startsWith(ActionCommand.PREFIX)) {
      return new ActionCommand(string, line);
    }

    if (actualString.startsWith(StopCommand.PREFIX)) {
      return new StopCommand(string, line);
    }

    if (actualString.startsWith(AssertFocused.PREFIX)) {
      return new AssertFocused(string, line);
    }

    return new AlphaNumericTypeCommand(string, line);
  }

  private void setDone() {
    myActionCallback.setDone();
  }

  public void stop() {
    myStopRequested = true;
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

  public static void main(String[] args) {
    final JFrame frame = new JFrame();
    frame.getContentPane().setLayout(new BorderLayout());
    final JPanel content = new JPanel(new BorderLayout());
    frame.getContentPane().add(content, BorderLayout.CENTER);

    final JTextArea textArea = new JTextArea();
    content.add(textArea, BorderLayout.CENTER);

    frame.setBounds(300, 300, 300, 300);
    frame.show();

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        textArea.requestFocus();
        start();
      }
    });
  }
  
  private static void start() {
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
      public boolean dispatchKeyEvent(KeyEvent e) {
        switch (e.getID()) {
          case KeyEvent.KEY_PRESSED:
            break;
          case KeyEvent.KEY_RELEASED:
            break;
          case KeyEvent.KEY_TYPED:
            break;
        }

        return false;
      }
    });

    new PlaybackRunner("%type", new StatusCallback() {
      public void error(String text, int currentLine) {
        System.out.println("Error: " + currentLine + " " + text);
      }

      public void message(String text, int currentLine) {
        System.out.println(currentLine + " " + text);
      }
    }, false).run();
  }


}
