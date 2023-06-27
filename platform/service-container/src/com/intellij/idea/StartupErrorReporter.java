// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import com.intellij.ide.BootstrapBundle;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.jetbrains.annotations.Nls.Capitalization.Sentence;
import static org.jetbrains.annotations.Nls.Capitalization.Title;

@ApiStatus.Internal
public final class StartupErrorReporter {
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
    return AppMode.isDroidFactory() ? "https://code.google.com/p/android/issues" : "https://jb.gg/ide/critical-startup-errors";
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
    catch (Throwable ignore) { }

    try {
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

      var type = error ? JOptionPane.ERROR_MESSAGE : JOptionPane.WARNING_MESSAGE;
      JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), scrollPane, title, type);
    }
    catch (Throwable t) {
      stream.println("\nAlso, a UI exception occurred on an attempt to show the above message");
      t.printStackTrace(stream);
    }
  }
}
