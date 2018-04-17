// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.playback.PlaybackCommand;
import com.intellij.openapi.ui.playback.PlaybackContext;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import java.io.File;

public abstract class AbstractCommand implements PlaybackCommand {
  private static final Logger LOG = Logger.getInstance("#" + AbstractCommand.class.getPackage().getName());

  public static final String CMD_PREFIX = "%";

  private final String myText;
  private final int myLine;
  private final boolean myExecuteInAwt;

  private File myScriptDir;

  public AbstractCommand(String text, int line) {
    this(text, line, false);
  }

  public AbstractCommand(String text, int line, boolean executeInAwt) {
    myExecuteInAwt = executeInAwt;
    myText = text;
    myLine = line;
  }

  public String getText() {
    return myText;
  }

  public int getLine() {
    return myLine;
  }

  public boolean canGoFurther() {
    return true;
  }

  public final Promise<Object> execute(final PlaybackContext context) {
    try {
      if (isToDumpCommand()) {
        dumpCommand(context);
      }
      final AsyncPromise<Object> result = new AsyncPromise<>();
      Runnable runnable = () -> {
        try {
          _execute(context).processed(result);
        }
        catch (Throwable e) {
          LOG.error(e);
          context.error(e.getMessage(), getLine());
          result.setError(e);
        }
      };

      if (isAwtThread()) {
        // prevent previous action context affecting next action.
        // E.g. previous action may have called callback.setDone from inside write action, while
        // next action may not expect that

        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(runnable);
      }
      else {
        ApplicationManager.getApplication().executeOnPooledThread(runnable);
      }

     return result;
    }
    catch (Throwable e) {
      context.error(e.getMessage(), getLine());
      return Promises.rejectedPromise(e);
    }
  }

  protected boolean isToDumpCommand() {
    return true;
  }

  protected boolean isAwtThread() {
    return myExecuteInAwt;
  }

  protected abstract Promise<Object> _execute(PlaybackContext context);

  public void dumpCommand(PlaybackContext context) {
    context.code(getText(), getLine());
  }

  public void dumpError(PlaybackContext context, final String text) {
    context.error(text, getLine());
  }

  @Override
  public File getScriptDir() {
    return myScriptDir;
  }


  public PlaybackCommand setScriptDir(File scriptDir) {
    myScriptDir = scriptDir;
    return this;
  }
}
