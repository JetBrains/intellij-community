// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.diagnostic.DefaultIdeaErrorLogger;
import com.intellij.diagnostic.LoadingState;
import com.intellij.diagnostic.VMOptions;
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector;
import com.intellij.ide.plugins.PluginUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MutedErrorLogger extends MutedLogger {

  private static final int FREQUENCY = Integer.getInteger("ide.muted.error.logger.frequency", 10);

  @NotNull
  @Contract("_ -> new")
  public static Logger of(@NotNull Logger delegate) {
    return new MutedErrorLogger(delegate);
  }

  public static boolean isEnabled() {
    return !Boolean.getBoolean("ide.muted.error.logger.disabled");
  }

  private MutedErrorLogger(@NotNull Logger delegate) {
    super(delegate);
  }

  @Override
  protected void logAdded(int hash, @NotNull Throwable t) {
    log("Hash for the following exception is '" + hash + "': " + t);
  }

  @Override
  protected void logOccurrences(int hash, @NotNull Throwable t, int occurrences) {
    reportToFus(t);
    if (occurrences % FREQUENCY == 0) {
      logRemoved(hash, occurrences);
    }
  }

  private static void reportToFus(@NotNull Throwable t) {
    Application application = ApplicationManager.getApplication();
    if (application != null && !application.isUnitTestMode() && !application.isDisposed()) {
      PluginId pluginId = PluginUtil.getInstance().findPluginId(t);
      VMOptions.MemoryKind kind = DefaultIdeaErrorLogger.getOOMErrorKind(t);
      LifecycleUsageTriggerCollector.onError(pluginId, t, kind);
    }
  }

  @Override
  protected void logRemoved(int hash, int occurrences) {
    if (occurrences > 1) {
      log("Exception with the following hash '" + hash + "' was reported " + occurrences + " times");
    }
  }

  private void log(@NotNull String message) {
    myDelegate.error(message, (Throwable)null);
  }

  @Override
  public void error(Object message) {
    if (message instanceof IdeaLoggingEvent) {
      Throwable t = ((IdeaLoggingEvent)message).getThrowable();
      if (!shouldBeReported(t)) {
        return;
      }
    }
    myDelegate.error(message);
  }

  @Override
  public void error(String message, @Nullable Throwable t, Attachment @NotNull ... attachments) {
    if (shouldBeReported(t)) {
      myDelegate.error(message, t, attachments);
    }
  }

  @Override
  public void error(String message, @Nullable Throwable t, String @NotNull ... details) {
    if (shouldBeReported(t)) {
      myDelegate.error(message, t, details);
    }
  }

  @Override
  public void error(String message, @Nullable Throwable t) {
    if (shouldBeReported(t)) {
      myDelegate.error(message, t);
    }
  }

  @Override
  public void error(@NotNull Throwable t) {
    if (shouldBeReported(t)) {
      myDelegate.error(t);
    }
  }

  private boolean shouldBeReported(@Nullable Throwable t) {
    return !LoadingState.COMPONENTS_LOADED.isOccurred() || !isAlreadyReported(t);
  }
}
