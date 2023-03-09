// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.playback.PlaybackCommand;
import com.intellij.openapi.ui.playback.PlaybackContext;
import io.opentelemetry.context.Context;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.io.File;
import java.util.ArrayList;

public abstract class AbstractCommand implements PlaybackCommand {

  private static final Logger LOG = Logger.getInstance(AbstractCommand.class);

  public static final @NonNls String CMD_PREFIX = "%";

  /**
   * "%commandName some parameters" => "some parameters"
   * OR
   * "some parameters" => "some parameters"
   */
  public String extractCommandArgument(String prefix) {
    if (myText.startsWith(prefix)) {
      return myText.substring(prefix.length()).trim();
    }
    else {
      return myText;
    }
  }

  public ArrayList<String> extractCommandList(String prefix, String delimiter) {
    ArrayList<String> arguments = new ArrayList<>();
    for (String argument: extractCommandArgument(prefix).split(delimiter)) {
      arguments.add(argument.trim());
    }
    return arguments;
  }

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
      Runnable runnable = Context.current().wrap(() -> {
        try {
          _execute(context).processed(result);
        }
        catch (Throwable e) {
          LOG.error(e);
          dumpError(context, e.getMessage());
          result.setError(e);
        }
      });

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
