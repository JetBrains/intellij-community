// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.diagnostic.ImplementationConflictException;
import com.intellij.diagnostic.LoadingState;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.BootstrapBundle;
import com.intellij.idea.AppExitCodes;
import com.intellij.idea.StartupErrorReporter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import java.util.stream.Collectors;

public final class StartupAbortedException extends RuntimeException {
  private static boolean hasGraphics = true;

  public StartupAbortedException(@NotNull String message, @NotNull Throwable cause) {
    super(message, cause);
  }

  public static void processException(@NotNull Throwable t) {
    if (LoadingState.COMPONENTS_LOADED.isOccurred() && !(t instanceof StartupAbortedException)) {
      if (!(t instanceof ControlFlowException)) {
        PluginManagerCore.getLogger().error(t);
      }
      return;
    }

    logAndExit(t, null);
  }

  public static void logAndExit(@NotNull Throwable t, @Nullable Logger log) {
    PluginManagerCore.EssentialPluginMissingException essentialPluginMissingException = findCause(t, PluginManagerCore.EssentialPluginMissingException.class);
    if (essentialPluginMissingException != null && essentialPluginMissingException.pluginIds != null) {
      StartupErrorReporter.showMessage(BootstrapBundle.message("bootstrap.error.title.corrupted.installation"),
                                       BootstrapBundle.message("bootstrap.error.message.missing.essential.plugins.0.1.please.reinstall.2",
                                               essentialPluginMissingException.pluginIds.size(),
                                               essentialPluginMissingException.pluginIds.stream().sorted().collect(Collectors.joining("\n  ", "  ", "\n\n")),
                                               getProductNameSafe()), true);
      System.exit(AppExitCodes.INSTALLATION_CORRUPTED);
    }

    PluginException pluginException = findCause(t, PluginException.class);
    PluginId pluginId = pluginException != null ? pluginException.getPluginId() : null;

    if ((log != null || Logger.isInitialized()) && !(t instanceof ProcessCanceledException)) {
      try {
        (log == null ? PluginManagerCore.getLogger() : log).error(t);
      }
      catch (Throwable ignore) {
      }

      // workaround for SOE on parsing PAC file (JRE-247)
      if (t instanceof StackOverflowError && "Nashorn AST Serializer".equals(Thread.currentThread().getName())) {
        return;
      }
    }

    if (LoadingState.COMPONENTS_REGISTERED.isOccurred()) {
      ImplementationConflictException conflictException = findCause(t, ImplementationConflictException.class);
      if (conflictException != null) {
        PluginConflictReporter pluginConflictReporter = ApplicationManager.getApplication().getService(PluginConflictReporter.class);
        pluginConflictReporter.reportConflict(conflictException.getConflictingPluginIds(), conflictException.isConflictWithPlatform());
      }
    }

    if (pluginId != null && !ApplicationInfoImpl.getShadowInstance().isEssentialPlugin(pluginId)) {
      PluginManagerCore.disablePlugin(pluginId);

      StringWriter message = new StringWriter();
      message.append(BootstrapBundle.message("bootstrap.error.message.plugin.0.failed.to.initialize.and.will.be.disabled.please.restart.1",
                                             pluginId.getIdString(),
                                             getProductNameSafe()));
      message.append("\n\n");

      Throwable cause = pluginException.getCause();
      Objects.requireNonNullElse(cause, pluginException).printStackTrace(new PrintWriter(message));

      StartupErrorReporter.showMessage(BootstrapBundle.message("bootstrap.error.title.plugin.error"), message.toString(), false); //NON-NLS
      System.exit(AppExitCodes.PLUGIN_ERROR);
    }
    else {
      StartupErrorReporter.showMessage(BootstrapBundle.message("bootstrap.error.title.start.failed"), t);
      System.exit(AppExitCodes.STARTUP_EXCEPTION);
    }
  }

  private static String getProductNameSafe() {
    try {
      return ApplicationNamesInfo.getInstance().getFullProductName();
    }
    catch (Throwable ignore) {
      return "the IDE";
    }
  }

  private static <T extends Throwable> T findCause(Throwable t, Class<T> clazz) {
    while (t != null) {
      if (clazz.isInstance(t)) {
        return clazz.cast(t);
      }
      t = t.getCause();
    }
    return null;
  }
}
