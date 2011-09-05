/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Set;

public class PlaybackContext  {
  
  private PlaybackRunner.StatusCallback myCallback;
  private int myCurrentLine;
  private Robot myRobot;
  private boolean myUseDirectActionCall;
  private PlaybackCommand myCurrentCmd;
  private File myBaseDir;
  private Set<Class> myCallClasses;
  private PlaybackRunner myRunner;

  public PlaybackContext(PlaybackRunner runner, PlaybackRunner.StatusCallback callback, int currentLine, Robot robot, boolean useDriectActionCall, PlaybackCommand currentCmd, File baseDir, Set<Class> callClasses) {
    myRunner = runner;
    myCallback = callback;
    myCurrentLine = currentLine;
    myRobot = robot;
    myUseDirectActionCall = useDriectActionCall;
    myCurrentCmd = currentCmd;
    myBaseDir = baseDir;
    myCallClasses = callClasses;
  }

  public PlaybackRunner.StatusCallback getCallback() {
    return myCallback;
  }

  public int getCurrentLine() {
    return myCurrentLine;
  }

  public Robot getRobot() {
    return myRobot;
  }

  public boolean isUseDirectActionCall() {
    return myUseDirectActionCall;
  }

  public PlaybackCommand getCurrentCmd() {
    return myCurrentCmd;
  }
  
  public File getBaseDir() {
    return myBaseDir != null ? myBaseDir : new File(System.getProperty("user.dir"));
  }
  
  public PathMacro getPathMacro() {
    return new PathMacro().setScriptDir(getCurrentCmd().getScriptDir()).setBaseDir(getBaseDir());
  }

  public void setBaseDir(File dir) {
    myBaseDir = dir;
  }

  public Set<Class> getCallClasses() {
    return myCallClasses;
  }

  public void flushAwtAndRun(final Runnable runnable) {
    if (EventQueue.isDispatchThread()) {
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          getRobot().waitForIdle();
          SwingUtilities.invokeLater(runnable);
        }
      });
    } else {
      getRobot().waitForIdle();
      runnable.run();
    }
  }

  public void error(String text, int currentLine) {
    getCallback().error(myRunner, text, currentLine);
  }

  public void message(String text, int currentLine) {
    getCallback().message(myRunner, text, currentLine);
  }

  public void code(String text, int currentLine) {
    getCallback().code(myRunner, text, currentLine);
  }
}
