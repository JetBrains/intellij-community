// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

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
    return new ArrayList<>(Arrays.stream(extractCommandArgument(prefix).split(delimiter))
                             .map(argument -> argument.trim())
                             .filter(argument -> !argument.isEmpty())
                             .toList());
  }

  private final @NonNls @NotNull String myText;
  private final int myLine;
  private final boolean executeInAwt;

  private @Nullable File myScriptDir;

  public AbstractCommand(@NotNull String text, int line) {
    this(text, line, false);
  }

  public AbstractCommand(@NotNull String text, int line, boolean executeInAwt) {
    this.executeInAwt = executeInAwt;
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
  public final @NotNull CompletableFuture<?> execute(@NotNull PlaybackContext context) {
    try {
      if (isToDumpCommand()) {
        context.code(getText(), getLine());
      }

      CompletableFuture<Object> result = new CompletableFuture<>();
      Runnable runnable = Context.current().wrap(() -> {
        try {
          Promises.asCompletableFuture(_execute(context)).whenComplete((o, throwable) -> {
            if (throwable == null) {
              result.complete(o);
            }
            else {
              result.completeExceptionally(throwable);
            }
          });
        }
        catch (Throwable e) {
          LOG.error(e);
          dumpError(context, e.getMessage());
          result.completeExceptionally(e);
        }
      });

      Application application = ApplicationManager.getApplication();
      if (executeInAwt) {
        // Prevent previous action context affecting next action.
        // E.g., previous action may have called callback.setDone from inside write action, while
        // the next action may not expect that
        application.invokeLater(runnable);
      }
      else {
        application.executeOnPooledThread(runnable);
      }
      return result;
    }
    catch (Throwable e) {
      dumpError(context, e.getMessage());
      return CompletableFuture.failedFuture(e);
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
