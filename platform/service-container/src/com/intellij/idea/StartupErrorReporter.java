// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import com.intellij.ide.BootstrapBundle;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.*;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Properties;

@ApiStatus.Internal
public final class StartupErrorReporter {
  private static boolean hasGraphics = true;

  public static void showMessage(@Nls(capitalization = Nls.Capitalization.Title) String title, Throwable t) {
      @Nls(capitalization = Nls.Capitalization.Sentence) StringWriter message = new StringWriter();

      AWTError awtError = findGraphicsError(t);
      if (awtError != null) {
        message.append(BootstrapBundle.message("bootstrap.error.message.failed.to.initialize.graphics.environment"));
        message.append("\n\n");
        hasGraphics = false;
        t = awtError;
      }
      else {
        message.append(BootstrapBundle.message("bootstrap.error.message.internal.error.please.refer.to.0", supportUrl()));
        message.append("\n\n");
      }

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
      Properties sp = System.getProperties();
      String jre = sp.getProperty("java.runtime.version", sp.getProperty("java.version", "(unknown)"));
      String vendor = sp.getProperty("java.vendor", "(unknown vendor)");
      String arch = sp.getProperty("os.arch", "(unknown arch)");
      String home = sp.getProperty("java.home", "(unknown java.home)");
      return jre + ' ' + arch + " (" + vendor + ")\n" + home;
    }

    private static @NlsSafe String supportUrl() {
      boolean studio = "AndroidStudio".equalsIgnoreCase(System.getProperty(AppMode.PLATFORM_PREFIX_PROPERTY));
      return studio ? "https://code.google.com/p/android/issues" : "https://jb.gg/ide/critical-startup-errors";
    }

    @SuppressWarnings({"UndesirableClassUsage", "UseOfSystemOutOrSystemErr"})
    public static void showMessage(@Nls(capitalization = Nls.Capitalization.Title) String title,
                                   @Nls(capitalization = Nls.Capitalization.Sentence) String message,
                                   boolean error) {
      PrintStream stream = error ? System.err : System.out;
      stream.println();
      stream.println(title);
      stream.println(message);

      if (!hasGraphics || AppMode.isCommandLine() || GraphicsEnvironment.isHeadless() || AppMode.isIsRemoteDevHost()) {
        return;
      }

      try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      }
      catch (Throwable ignore) { }

      try {
        JTextPane textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setText(message.replaceAll("\t", "    "));
        textPane.setBackground(UIManager.getColor("Panel.background"));
        textPane.setCaretPosition(0);
        JScrollPane scrollPane =
          new JScrollPane(textPane, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);

        int maxHeight = Toolkit.getDefaultToolkit().getScreenSize().height / 2;
        int maxWidth = Toolkit.getDefaultToolkit().getScreenSize().width / 2;
        Dimension component = scrollPane.getPreferredSize();
        if (component.height > maxHeight || component.width > maxWidth) {
          scrollPane.setPreferredSize(new Dimension(Math.min(maxWidth, component.width), Math.min(maxHeight, component.height)));
        }

        int type = error ? JOptionPane.ERROR_MESSAGE : JOptionPane.WARNING_MESSAGE;
        JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), scrollPane, title, type);
      }
      catch (Throwable t) {
        stream.println("\nAlso, a UI exception occurred on an attempt to show the above message");
        t.printStackTrace(stream);
      }
    }
}
