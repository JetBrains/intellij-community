// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.playback;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.Set;

public abstract class PlaybackContext  {
  
  private final PlaybackRunner.StatusCallback myCallback;
  private final int myCurrentLine;
  private final Robot myRobot;
  private final boolean myUseDirectActionCall;
  private final PlaybackCommand myCurrentCmd;
  private File myBaseDir;
  private final Set<Class<?>> myCallClasses;
  protected final PlaybackRunner myRunner;
  private final boolean myUseTypingTargets;

  public PlaybackContext(PlaybackRunner runner, PlaybackRunner.StatusCallback callback, int currentLine, Robot robot, boolean useDriectActionCall, boolean useTypingTargets, PlaybackCommand currentCmd, File baseDir, Set<Class<?>> callClasses) {
    myRunner = runner;
    myCallback = callback;
    myCurrentLine = currentLine;
    myRobot = robot;
    myUseDirectActionCall = useDriectActionCall;
    myUseTypingTargets = useTypingTargets;
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
    if (myRobot == null) {
      throw new RuntimeException("Robot is not available in the headless mode");
    }
    return myRobot;
  }

  public boolean isUseDirectActionCall() {
    return myUseDirectActionCall;
  }

  public boolean isUseTypingTargets() {
    return myUseTypingTargets;
  }

  public PlaybackCommand getCurrentCmd() {
    return myCurrentCmd;
  }
  
  public File getBaseDir() {
    return myBaseDir != null ? myBaseDir : new File(System.getProperty("user.dir"));
  }
  
  @ApiStatus.Internal
  public PathMacro getPathMacro() {
    return new PathMacro().setScriptDir(getCurrentCmd().getScriptDir()).setBaseDir(getBaseDir());
  }

  public void setBaseDir(File dir) {
    myBaseDir = dir;
  }

  public Set<Class<?>> getCallClasses() {
    return myCallClasses;
  }
  
  public void runPooledThread(Runnable runnable) {
    ApplicationManager.getApplication().executeOnPooledThread(runnable);
  }
  
  public void error(String text, int currentLine) {
    getCallback().message(this, text, PlaybackRunner.StatusCallback.Type.error);
  }

  public void message(String text, int currentLine) {
    getCallback().message(this, text, PlaybackRunner.StatusCallback.Type.message);
  }

  public void test(String text, int currentLine) {
    getCallback().message(this, text, PlaybackRunner.StatusCallback.Type.test);
  }

  public void code(String text, int currentLine) {
    getCallback().message(this, text, PlaybackRunner.StatusCallback.Type.code);
  }

  public abstract void pushStage(StageInfo info);

  public abstract StageInfo popStage();

  public abstract int getCurrentStageDepth();

  public abstract void addPassed(StageInfo stage);

  public abstract boolean isDisposed();

  public abstract void storeRegistryValue(String key);

  public abstract void setProject(@Nullable Project project);

  public abstract @NotNull Project getProject();
}
