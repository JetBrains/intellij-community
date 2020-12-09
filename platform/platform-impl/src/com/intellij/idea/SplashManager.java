// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.wm.impl.FrameBoundsConverter;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.Splash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class SplashManager {
  private static JFrame PROJECT_FRAME;
  static Splash SPLASH_WINDOW;

  public static void show(String @NotNull [] args, boolean visible) {
    for (String arg : args) {
      if (CommandLineArgs.NO_SPLASH.equals(arg)) {
        System.setProperty(CommandLineArgs.NO_SPLASH, "true");
        return;
      }
    }

    Activity frameActivity = StartUpMeasurer.startActivity("splash as project frame initialization");
    try {
      PROJECT_FRAME = createFrameIfPossible();
    }
    catch (Throwable e) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println(e.getMessage());
    }
    frameActivity.end();

    if (PROJECT_FRAME != null) {
      return;
    }

    // must be out of activity measurement
    ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
    assert SPLASH_WINDOW == null;
    Activity activity = StartUpMeasurer.startActivity("splash initialization");
    SPLASH_WINDOW = new Splash(appInfo);
    EventQueue.invokeLater(() -> {
      Splash splash = SPLASH_WINDOW;
      // can be cancelled if app was started very fast
      if (splash != null) {
        splash.initAndShow(visible);
      }
      activity.end();
    });
  }

  private static @Nullable IdeFrameImpl createFrameIfPossible() throws IOException {
    Path infoFile = Paths.get(PathManager.getSystemPath(), "lastProjectFrameInfo");
    ByteBuffer buffer;
    try (SeekableByteChannel channel = Files.newByteChannel(infoFile)) {
      buffer = ByteBuffer.allocate((int)channel.size());
      do {
        channel.read(buffer);
      } while (buffer.hasRemaining());

      buffer.flip();
      if (buffer.getShort() != 0) {
        return null;
      }
    }
    catch (NoSuchFileException ignore) {
      return null;
    }

    Rectangle savedBounds = new Rectangle(buffer.getInt(), buffer.getInt(), buffer.getInt(), buffer.getInt());
    //noinspection UseJBColor
    Color backgroundColor = new Color(buffer.getInt(), /* hasAlpha = */ true);
    @SuppressWarnings("unused")
    boolean isFullScreen = buffer.get() == 1;
    int extendedState = buffer.getInt();

    IdeFrameImpl frame = new IdeFrameImpl();
    frame.setAutoRequestFocus(false);
    frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

    frame.setBounds(FrameBoundsConverter.convertFromDeviceSpaceAndFitToScreen(savedBounds));
    frame.setExtendedState(extendedState);

    frame.setMinimumSize(new Dimension(340, (int)frame.getMinimumSize().getHeight()));
    frame.setBackground(backgroundColor);
    frame.getContentPane().setBackground(backgroundColor);
    if (SystemInfoRt.isMac) {
      frame.setIconImage(null);
    }

    StartUpMeasurer.addInstantEvent("frame shown");
    Activity activity = StartUpMeasurer.startActivity("frame set visible");
    frame.setVisible(true);
    activity.end();
    return frame;
  }

  public static void executeWithHiddenSplash(@NotNull Window window, @NotNull Runnable runnable) {
    if (SPLASH_WINDOW == null) {
      if (PROJECT_FRAME != null) {
        // just destroy frame
        Runnable task = getHideTask();
        if (task != null) {
          task.run();
        }
      }
      runnable.run();
      return;
    }

    WindowListener listener = new WindowAdapter() {
      @Override
      public void windowOpened(WindowEvent e) {
        setVisible(false);
      }
    };
    window.addWindowListener(listener);

    runnable.run();

    setVisible(true);
    window.removeWindowListener(listener);
  }

  private static void setVisible(boolean value) {
    Splash splash = SPLASH_WINDOW;
    if (splash != null) {
      splash.setVisible(value);
      if (value) {
        splash.paint(splash.getGraphics());
      }
    }
  }

  public static @Nullable JFrame getAndUnsetProjectFrame() {
    JFrame frame = PROJECT_FRAME;
    PROJECT_FRAME = null;
    return frame;
  }

  public static void hideBeforeShow(@NotNull Window window) {
    Runnable hideSplashTask = getHideTask();
    if (hideSplashTask != null) {
      window.addWindowListener(new WindowAdapter() {
        @Override
        public void windowOpened(WindowEvent e) {
          hideSplashTask.run();
          window.removeWindowListener(this);
        }
      });
    }
  }

  public static @Nullable Runnable getHideTask() {
    Window window = SPLASH_WINDOW;
    if (window == null) {
      window = PROJECT_FRAME;
      if (window == null) {
        return null;
      }
    }

    Ref<Window> ref = new Ref<>(window);
    SPLASH_WINDOW = null;
    PROJECT_FRAME = null;

    return () -> {
      Window w = ref.get();
      if (w != null) {
        ref.set(null);
        w.setVisible(false);
        w.dispose();
      }
    };
  }
}
