// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.diagnostic.ImplementationConflictException;
import com.intellij.diagnostic.LoadingState;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.BootstrapBundle;
import com.intellij.idea.Main;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.NotNull;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.stream.Collectors;

public final class StartupAbortedException extends RuntimeException {
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

    logAndExit(t);
  }

  public static void logAndExit(@NotNull Throwable t) {
    PluginManagerCore.EssentialPluginMissingException essentialPluginMissingException = findCause(t, PluginManagerCore.EssentialPluginMissingException.class);
    if (essentialPluginMissingException != null && essentialPluginMissingException.pluginIds != null) {
      Main.showMessage(BootstrapBundle.message("bootstrap.error.title.corrupted.installation"),
                       BootstrapBundle.message("bootstrap.error.message.missing.essential.plugins.0.1.please.reinstall.2",
                                               essentialPluginMissingException.pluginIds.size(),
                                               essentialPluginMissingException.pluginIds.stream().sorted().collect(Collectors.joining("\n  ", "  ", "\n\n")),
                                               getProductNameSafe()), true);
      System.exit(Main.INSTALLATION_CORRUPTED);
    }

    PluginException pluginException = findCause(t, PluginException.class);
    PluginId pluginId = pluginException != null ? pluginException.getPluginId() : null;

    if (Logger.isInitialized() && !(t instanceof ProcessCanceledException)) {
      try {
        PluginManagerCore.getLogger().error(t);
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
        pluginConflictReporter.reportConflictByClasses(conflictException.getConflictingClasses());
      }
    }

    if (pluginId != null && !ApplicationInfoImpl.getShadowInstance().isEssentialPlugin(pluginId)) {
      PluginManagerCore.disablePlugin(pluginId);

      StringWriter message = new StringWriter();
      message.append(BootstrapBundle.message("bootstrap.error.message.plugin.0.failed.to.initialize.and.will.be.disabled.please.restart.1",
                                             pluginId.getIdString(),
                                             getProductNameSafe()));
      message.append("\n\n");
      pluginException.getCause().printStackTrace(new PrintWriter(message));

      Main.showMessage(BootstrapBundle.message("bootstrap.error.title.plugin.error"), message.toString(), false); //NON-NLS
      System.exit(Main.PLUGIN_ERROR);
    }
    else {
      Main.showMessage(BootstrapBundle.message("bootstrap.error.title.start.failed"), t);
      System.exit(Main.STARTUP_EXCEPTION);
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
