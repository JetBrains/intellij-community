// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap;

import com.intellij.diagnostic.ImplementationConflictException;
import com.intellij.diagnostic.LoadingState;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.BootstrapBundle;
import com.intellij.ide.logsUploader.LogUploader;
import com.intellij.ide.plugins.EssentialPluginMissingException;
import com.intellij.ide.plugins.PluginConflictReporter;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.StartupAbortedException;
import com.intellij.idea.AppExitCodes;
import com.intellij.idea.AppMode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ConfigBackup;
import com.intellij.openapi.application.CustomConfigMigrationOption;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.ExceptionWithAttachments;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.util.io.Compressor;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutionException;

import static java.util.Objects.requireNonNullElse;
import static org.jetbrains.annotations.Nls.Capitalization.Sentence;
import static org.jetbrains.annotations.Nls.Capitalization.Title;

@ApiStatus.Internal
public final class StartupErrorReporter {
  private static final String SUPPORT_URL_PROPERTY = "ij.startup.error.support.url";
  private static final String REPORT_URL_PROPERTY = "ij.startup.error.report.url";

  private static boolean hasGraphics = !ApplicationManagerEx.isInIntegrationTest();

  public static void pluginInstallationProblem(Throwable t) {
    showWarning(
      "Plugin Installation Problem",
      "The IDE failed to install or update some plugins.\n" +
      "Please try again, and if the problem persists, report it to the support.\n\n" +
      "The cause: " + t.toString());
  }

  /** <strong>Note:</strong> warnings should be hardcoded because it's too early to try loading localization plugins. */
  @SuppressWarnings({"UseOfSystemOutOrSystemErr", "HardCodedStringLiteral"})
  public static void showWarning(@NonNls String title, @NonNls String message) {
    System.out.println();
    System.out.println("**" + title + "**");
    System.out.println();
    System.out.println(message);

    try {
      JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), prepareMessage(message), title, JOptionPane.WARNING_MESSAGE);
    }
    catch (Throwable t) {
      System.err.println("\n-----");
      t.printStackTrace(System.err);
    }
  }

  public static void showError(@NotNull @Nls(capitalization = Title) String title, @NotNull Throwable t) {
    var message = new StringWriter();

    var awtError = findCause(t, AWTError.class);
    if (awtError != null) {
      message.append(BootstrapBundle.message("bootstrap.error.prefix.graphics"));
      hasGraphics = false;
      t = awtError;
    }
    else {
      message.append(BootstrapBundle.message("bootstrap.error.prefix.other"));
    }

    message.append("\n\n");
    t.printStackTrace(new PrintWriter(message));

    message.append("\n-----\n").append(BootstrapBundle.message("bootstrap.error.appendix.jre", jreDetails()));

    showError(title, message.toString(), t); //NON-NLS
  }

  private static @NlsSafe String jreDetails() {
    var sp = System.getProperties();
    var jre = sp.getProperty("java.runtime.version", sp.getProperty("java.version", "(unknown)"));
    var vendor = sp.getProperty("java.vendor", "(unknown vendor)");
    var arch = sp.getProperty("os.arch", "(unknown arch)");
    var home = sp.getProperty("java.home", "(unknown java.home)");
    return jre + ' ' + arch + " (" + vendor + ")\n" + home;
  }

  public static void showError(@Nls(capitalization = Title) String title, @Nls(capitalization = Sentence) String message) {
    showError(title, message, null);
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void showError(@Nls(capitalization = Title) String title, @Nls(capitalization = Sentence) String message, @Nullable Throwable error) {
    System.err.println();
    System.err.println("**" + title + "**");
    System.err.println();
    System.err.println(message);

    if (!hasGraphics || AppMode.isCommandLine() || GraphicsEnvironment.isHeadless() || AppMode.isRemoteDevHost()) {
      return;
    }

    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    }
    catch (Throwable ignore) { }

    try {
      SplashManagerKt.hideSplash();
    }
    catch (Throwable ignore) { }

    try {
      var messageObj = prepareMessage(message);
      var close = BootstrapBundle.message("bootstrap.error.option.close");
      var iconUrl = StartupErrorReporter.class.getResource("/images/questionSign.png");
      var learnMore = iconUrl != null ? new JLabel(new ImageIcon(iconUrl)) : new JLabel("?");
      learnMore.setToolTipText(BootstrapBundle.message("bootstrap.error.option.support"));
      learnMore.setCursor(new Cursor(Cursor.HAND_CURSOR));
      learnMore.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          supportCenter();
        }
      });
      if (error != null) {
        var options = new Object[]{close, BootstrapBundle.message("bootstrap.error.option.reset"), BootstrapBundle.message("bootstrap.error.option.report"), learnMore};
        var choice = JOptionPane.showOptionDialog(
          JOptionPane.getRootFrame(), messageObj, title, JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, options, options[0]
        );
        switch (choice) {
          case 1 -> cleanStart();
          case 2 -> reportProblem(title, message, error);
        }
      }
      else {
        var options = new Object[]{close, learnMore};
        JOptionPane.showOptionDialog(
          JOptionPane.getRootFrame(), messageObj, title, JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, options, options[0]
        );
      }
    }
    catch (Throwable t) {
      System.err.println("\n-----");
      System.err.println(BootstrapBundle.message("bootstrap.error.appendix.graphics"));
      t.printStackTrace(System.err);
    }
  }

  private static void supportCenter() {
    try {
      var url = System.getProperty(SUPPORT_URL_PROPERTY, "https://jb.gg/ide/critical-startup-errors");
      Desktop.getDesktop().browse(new URI(url));
    }
    catch (Throwable t) {
      showBrowserError(t);
    }
  }

  private static void reportProblem(String title, String description, @Nullable Throwable error) {
    if (error != null) {
      title += " (" + error.getClass().getSimpleName() + ": " + shorten(error.getMessage()) + ')';
    }

    var uploadId = (String)null;
    if (error instanceof ExceptionWithAttachments ewa) {
      var message = prepareMessage(BootstrapBundle.message("bootstrap.error.message.confirm"));
      var ok = JOptionPane.showConfirmDialog(JOptionPane.getRootFrame(), message, BootstrapBundle.message("bootstrap.error.option.report"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE);
      if (ok != JOptionPane.OK_OPTION) return;

      try {
        uploadId = uploadLogs(ewa);
      }
      catch (Throwable t) {
        var buf = new StringWriter();
        t.printStackTrace(new PrintWriter(buf));
        message = prepareMessage(BootstrapBundle.message("bootstrap.error.message.no.logs", buf));
        JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), message, BootstrapBundle.message("bootstrap.error.title.no.logs"), JOptionPane.ERROR_MESSAGE);
        return;
      }
    }
    if (uploadId != null) {
      description += "\n\n-----\n[Upload ID: " + uploadId + ']';
    }

    try {
      var url = System.getProperty(REPORT_URL_PROPERTY, "https://youtrack.jetbrains.com/newissue?project=IJPL&clearDraft=true&summary=$TITLE$&description=$DESCR$&c=$SUBSYSTEM$")
        .replace("$TITLE$", URLUtil.encodeURIComponent(title))
        .replace("$DESCR$", URLUtil.encodeURIComponent(description))
        .replace("$SUBSYSTEM$", URLUtil.encodeURIComponent("Subsystem: IDE. Startup"));
      Desktop.getDesktop().browse(new URI(url));
    }
    catch (Throwable t) {
      showBrowserError(t);
    }
  }

  private static String shorten(String message) {
    if (message.length() <= 200) return message;
    int p = message.indexOf('\n', 200);
    if (p < 0 || p >= 250) p = message.indexOf(". ", 200);
    if (p < 0 || p >= 250) p = message.indexOf(' ', 200);
    if (p < 0 || p >= 250) p = 200;
    message = message.substring(0, p);
    return message + (message.endsWith(".") ? ".." : "...");
  }

  private static @Nullable String uploadLogs(ExceptionWithAttachments error) throws ExecutionException, InterruptedException {
    var progressBar = new JProgressBar();
    progressBar.setIndeterminate(true);
    var label = new JLabel(BootstrapBundle.message("bootstrap.error.message.logs"));
    var panel = new JPanel(new BorderLayout(5, 5));
    panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    panel.add(label, BorderLayout.NORTH);
    panel.add(progressBar, BorderLayout.CENTER);
    var progressDialog = new JDialog(JOptionPane.getRootFrame(), BootstrapBundle.message("bootstrap.error.title.logs"), true);
    progressDialog.add(panel);
    progressDialog.setSize(300, 100);
    progressDialog.setLocationRelativeTo(null);

    @SuppressWarnings("SSBasedInspection")
    var worker = new SwingWorker<String, Void>() {
      @Override
      protected String doInBackground() throws Exception {
        var logs = collectLogs(error);
        try {
          return LogUploader.uploadFile(logs);
        }
        finally {
          NioFiles.deleteQuietly(logs);
        }
      }

      @Override
      protected void done() {
        progressDialog.setVisible(false);
        progressDialog.dispose();
      }
    };

    worker.execute();
    progressDialog.setVisible(true);

    return worker.get();
  }

  private static Path collectLogs(ExceptionWithAttachments error) throws IOException {
    var ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
    var logs = Files.createTempFile("startup-err-logs-" + ts + '-', ".zip");

    try (var zip = new Compressor.Zip(logs)) {
      var log = PathManager.getLogDir().resolve("idea.log");
      if (Files.exists(log)) {
        zip.addFile(log.getFileName().toString(), log);
      }

      var productData = Path.of(PathManager.getHomePath(), "product-info.json");
      if (!Files.exists(productData)) {
        productData = Path.of(PathManager.getHomePath(), "Resources/product-info.json");
      }
      if (Files.exists(productData)) {
        zip.addFile(productData.getFileName().toString(), productData);
      }

      for (var attachment : error.getAttachments()) {
        zip.addFile(attachment.getName(), attachment.getBytes());
      }
    }

    return logs;
  }

  private static void cleanStart() {
    try {
      var backupPath = ConfigBackup.Companion.getNextBackupPath(PathManager.getConfigDir());
      CustomConfigMigrationOption.StartWithCleanConfig.INSTANCE.writeConfigMarkerFile();
      var message = BootstrapBundle.message("bootstrap.error.message.reset", backupPath);
      JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), message, BootstrapBundle.message("bootstrap.error.title.reset"), JOptionPane.INFORMATION_MESSAGE);
    }
    catch (Throwable t) {
      var message = BootstrapBundle.message("bootstrap.error.message.reset.failed", t);
      JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), message, BootstrapBundle.message("bootstrap.error.title.reset"), JOptionPane.ERROR_MESSAGE);
    }
  }

  private static void showBrowserError(Throwable t) {
    var message = prepareMessage(BootstrapBundle.message("bootstrap.error.message.browser", t));
    JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), message, BootstrapBundle.message("bootstrap.error.title.browser"), JOptionPane.ERROR_MESSAGE);
  }

  @SuppressWarnings({"UndesirableClassUsage", "HardCodedStringLiteral"})
  private static JScrollPane prepareMessage(String message) {
    var textPane = new JTextPane();
    textPane.setEditable(false);
    textPane.setText(message.replaceAll("\t", "    "));
    textPane.setBackground(UIManager.getColor("Panel.background"));
    textPane.setCaretPosition(0);

    var scrollPane = new JScrollPane(textPane, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setBorder(null);

    var maxHeight = Toolkit.getDefaultToolkit().getScreenSize().height / 2;
    var maxWidth = Toolkit.getDefaultToolkit().getScreenSize().width / 2;
    var paneSize = scrollPane.getPreferredSize();
    if (paneSize.height > maxHeight || paneSize.width > maxWidth) {
      scrollPane.setPreferredSize(new Dimension(Math.min(maxWidth, paneSize.width), Math.min(maxHeight, paneSize.height)));
    }
    return scrollPane;
  }

  public static void processException(@NotNull Throwable t) {
    if (LoadingState.COMPONENTS_LOADED.isOccurred() && !(t instanceof StartupAbortedException)) {
      if (!(t instanceof ControlFlowException)) {
        PluginManagerCore.getLogger().error(t);
      }
      return;
    }

    var essentialPluginMissingException = findCause(t, EssentialPluginMissingException.class);
    if (essentialPluginMissingException != null) {
      var pluginIds = essentialPluginMissingException.pluginIds;
      showError(
        BootstrapBundle.message("bootstrap.error.title.corrupted"),
        BootstrapBundle.message("bootstrap.error.essential.plugins", pluginIds.size(), "  " + String.join("\n  ", pluginIds) + "\n\n"));
      System.exit(AppExitCodes.INSTALLATION_CORRUPTED);
    }

    var pluginException = findCause(t, PluginException.class);
    var pluginId = pluginException == null ? null : pluginException.getPluginId();

    if (Logger.isInitialized() && !(t instanceof ProcessCanceledException)) {
      try {
        PluginManagerCore.getLogger().error(t);
      }
      catch (Throwable ignore) { }
      // workaround for SOE on parsing a PAC file (JRE-247)
      if (t instanceof StackOverflowError && "Nashorn AST Serializer".equals(Thread.currentThread().getName())) {
        return;
      }
    }

    if (LoadingState.COMPONENTS_REGISTERED.isOccurred()) {
      var conflictException = findCause(t, ImplementationConflictException.class);
      if (conflictException != null) {
        PluginConflictReporter pluginConflictReporter = ApplicationManager.getApplication().getService(PluginConflictReporter.class);
        pluginConflictReporter.reportConflict(conflictException.getConflictingPluginIds(), conflictException.isConflictWithPlatform());
      }
    }

    if (pluginId != null && !ApplicationInfoImpl.getShadowInstance().isEssentialPlugin(pluginId)) {
      PluginManagerCore.disablePlugin(pluginId);

      var message = new StringWriter();
      message.append(BootstrapBundle.message("bootstrap.error.message.plugin.failed", pluginId.getIdString()));
      message.append("\n\n");
      requireNonNullElse(pluginException.getCause(), pluginException).printStackTrace(new PrintWriter(message));

      showError(BootstrapBundle.message("bootstrap.error.title.plugin.init"), message.toString()); //NON-NLS
      System.exit(AppExitCodes.PLUGIN_ERROR);
    }
    else {
      showError(BootstrapBundle.message("bootstrap.error.title.start.failed"), t);
      System.exit(AppExitCodes.STARTUP_EXCEPTION);
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
