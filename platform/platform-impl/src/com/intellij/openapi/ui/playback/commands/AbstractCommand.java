// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.playback.PlaybackCommand;
import com.intellij.openapi.ui.playback.PlaybackContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.io.File;

public abstract class AbstractCommand implements PlaybackCommand {

  private static final Logger LOG = Logger.getInstance(AbstractCommand.class);

  public static final @NonNls String CMD_PREFIX = "%";

  private final @NonNls @NotNull String myText;
  private final int myLine;
  private final boolean myExecuteInAwt;

  private @Nullable File myScriptDir;

  public AbstractCommand(@NotNull String text, int line) {
    this(text, line, false);
  }

  public AbstractCommand(@NotNull String text, int line, boolean executeInAwt) {
    myExecuteInAwt = executeInAwt;
    myText = text;
    myLine = line;
  }

  public final @NonNls @NotNull String getText() {
    return myText;
  }

  public final int getLine() {
    return myLine;
  }

  @Override
  public final @Nullable File getScriptDir() {
    return myScriptDir;
  }

  public final void setScriptDir(@Nullable File scriptDir) {
    myScriptDir = scriptDir;
  }

  @Override
  public boolean canGoFurther() {
    return true;
  }

  @Override
  public final @NotNull Promise<Object> execute(@NotNull PlaybackContext context) {
    try {
      if (isToDumpCommand()) {
        context.code(getText(), getLine());
      }
      final AsyncPromise<Object> result = new AsyncPromise<>();
      Runnable runnable = () -> {
        try {
          _execute(context).processed(result);
        }
        catch (Throwable e) {
          LOG.error(e);
          dumpError(context, e.getMessage());
          result.setError(e);
        }
      };

      Application application = ApplicationManager.getApplication();
      if (myExecuteInAwt) {
        // prevent previous action context affecting next action.
        // E.g. previous action may have called callback.setDone from inside write action, while
        // next action may not expect that
        application.invokeLater(runnable);
      }
      else {
        application.executeOnPooledThread(runnable);
      }

      return result;
    }
    catch (Throwable e) {
      dumpError(context, e.getMessage());
      return Promises.rejectedPromise(e);
    }
  }

  protected boolean isToDumpCommand() {
    return true;
  }

  protected abstract @NotNull Promise<Object> _execute(@NotNull PlaybackContext context);

  protected final void dumpError(@NotNull PlaybackContext context, @NotNull String text) {
    context.error(text, getLine());
  }
}
