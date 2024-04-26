// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap;

import com.intellij.diagnostic.ImplementationConflictException;
import com.intellij.diagnostic.LoadingState;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.BootstrapBundle;
import com.intellij.ide.plugins.EssentialPluginMissingException;
import com.intellij.ide.plugins.PluginConflictReporter;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.StartupAbortedException;
import com.intellij.idea.AppExitCodes;
import com.intellij.idea.AppMode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.jetbrains.annotations.Nls.Capitalization.Sentence;
import static org.jetbrains.annotations.Nls.Capitalization.Title;

@ApiStatus.Internal
public final class StartupErrorReporter {
  private static final String STARTUP_ERROR_REPORTING_URL_PROPERTY = "intellij.custom.startup.error.reporting.url";
  private static boolean hasGraphics = !ApplicationManagerEx.isInIntegrationTest();

  public static void showMessage(@Nls(capitalization = Title) String title, Throwable t) {
    @Nls(capitalization = Sentence) var message = new StringWriter();

    var awtError = findGraphicsError(t);
    if (awtError != null) {
      message.append(BootstrapBundle.message("bootstrap.error.message.failed.to.initialize.graphics.environment"));
      hasGraphics = false;
      t = awtError;
    }
    else {
      message.append(BootstrapBundle.message("bootstrap.error.message.internal.error.please.refer.to.0", supportUrl()));
    }

    message.append("\n\n");
    t.printStackTrace(new PrintWriter(message));

    message.append("\n-----\n").append(BootstrapBundle.message("bootstrap.error.message.jre.details", jreDetails()));

    showMessage(title, message.toString(), true); //NON-NLS
  }

  private static AWTError findGraphicsError(Throwable t) {
    while (t != null) {
      if (t instanceof AWTError) {
        return (AWTError)t;
      }
      t = t.getCause();
    }
    return null;
  }

  private static @NlsSafe String jreDetails() {
    var sp = System.getProperties();
    var jre = sp.getProperty("java.runtime.version", sp.getProperty("java.version", "(unknown)"));
    var vendor = sp.getProperty("java.vendor", "(unknown vendor)");
    var arch = sp.getProperty("os.arch", "(unknown arch)");
    var home = sp.getProperty("java.home", "(unknown java.home)");
    return jre + ' ' + arch + " (" + vendor + ")\n" + home;
  }

  private static @NlsSafe String supportUrl() {
    return System.getProperty(STARTUP_ERROR_REPORTING_URL_PROPERTY, "https://jb.gg/ide/critical-startup-errors");
  }

  @SuppressWarnings({"UndesirableClassUsage", "UseOfSystemOutOrSystemErr", "ExtractMethodRecommender"})
  public static void showMessage(@Nls(capitalization = Title) String title, @Nls(capitalization = Sentence) String message, boolean error) {
    var stream = error ? System.err : System.out;
    stream.println();
    stream.println(title);
    stream.println(message);

    if (!hasGraphics || AppMode.isCommandLine() || GraphicsEnvironment.isHeadless() || AppMode.isRemoteDevHost()) {
      return;
    }

    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    }
    catch (Throwable ignore) {
    }

    try {
      SplashManagerKt.hideSplash();
    }
    catch (Throwable ignore) {
    }

    try {
      var textPane = new JTextPane();
      textPane.setEditable(false);
      textPane.setText(message.replaceAll("\t", "    "));
      textPane.setBackground(UIManager.getColor("Panel.background"));
      textPane.setCaretPosition(0);
      var scrollPane =
        new JScrollPane(textPane, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
      scrollPane.setBorder(null);

      var maxHeight = Toolkit.getDefaultToolkit().getScreenSize().height / 2;
      var maxWidth = Toolkit.getDefaultToolkit().getScreenSize().width / 2;
      var paneSize = scrollPane.getPreferredSize();
      if (paneSize.height > maxHeight || paneSize.width > maxWidth) {
        scrollPane.setPreferredSize(new Dimension(Math.min(maxWidth, paneSize.width), Math.min(maxHeight, paneSize.height)));
      }

      var type = error ? JOptionPane.ERROR_MESSAGE : JOptionPane.WARNING_MESSAGE;
      JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), scrollPane, title, type);
    }
    catch (Throwable t) {
      stream.println("\nAlso, a UI exception occurred on an attempt to show the above message");
      t.printStackTrace(stream);
    }
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
    EssentialPluginMissingException essentialPluginMissingException = findCause(t, EssentialPluginMissingException.class);
    if (essentialPluginMissingException != null) {
      showMessage(BootstrapBundle.message("bootstrap.error.title.corrupted.installation"),
                                       BootstrapBundle.message("bootstrap.error.message.missing.essential.plugins.0.1.please.reinstall.2",
                                                               essentialPluginMissingException.pluginIds.size(),
                                                               essentialPluginMissingException.pluginIds.stream().sorted().collect(
                                                                 Collectors.joining("\n  ", "  ", "\n\n")),
                                                               getProductNameSafe()), true);
      System.exit(AppExitCodes.INSTALLATION_CORRUPTED);
    }

    PluginException pluginException = findCause(t, PluginException.class);
    PluginId pluginId = pluginException == null ? null : pluginException.getPluginId();

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

      showMessage(BootstrapBundle.message("bootstrap.error.title.plugin.error"), message.toString(), false); //NON-NLS
      System.exit(AppExitCodes.PLUGIN_ERROR);
    }
    else {
      showMessage(BootstrapBundle.message("bootstrap.error.title.start.failed"), t);
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
