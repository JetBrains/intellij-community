// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

final class LibNotifyWrapper implements SystemNotificationsImpl.Notifier {
  private static LibNotifyWrapper ourInstance;

  static synchronized @Nullable LibNotifyWrapper getInstance() {
    if (ourInstance == null) {
      ourInstance = new LibNotifyWrapper();
    }
    return ourInstance;
  }

  static final class LibNotify {
    private final MethodHandle init;
    private final MethodHandle uninit;
    private final MethodHandle create;
    private final MethodHandle show;
    private final MethodHandle unref;

    LibNotify(SymbolLookup symbols) {
      var linker = Linker.nativeLinker();
      init = linker.downcallHandle(symbols.findOrThrow("notify_init"), FunctionDescriptor.of(JAVA_INT, ADDRESS));
      uninit = linker.downcallHandle(symbols.findOrThrow("notify_uninit"), FunctionDescriptor.ofVoid());
      create = linker.downcallHandle(symbols.findOrThrow("notify_notification_new"),
                                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS));
      show = linker.downcallHandle(symbols.findOrThrow("notify_notification_show"), FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
      unref = linker.downcallHandle(symbols.findOrThrow("g_object_unref"), FunctionDescriptor.ofVoid(ADDRESS));
    }

    void init(String appName) {
      try (var arena = Arena.ofConfined()) {
        if ((int)init.invokeExact(arena.allocateFrom(appName)) == 0) throw new IllegalStateException("notify_init failed");
      }
      catch (Throwable error) {
        throw new IllegalStateException(error);
      }
    }

    void uninit() {
      try {
        uninit.invokeExact();
      }
      catch (Throwable error) {
        throw new IllegalStateException(error);
      }
    }

    boolean notify(String title, String body, String icon) {
      try (var arena = Arena.ofConfined()) {
        var notification = (MemorySegment)create.invokeExact(arena.allocateFrom(title), arena.allocateFrom(body), arena.allocateFrom(icon));
        if (notification.address() == 0) return false;
        try {
          return (int)show.invokeExact(notification, MemorySegment.NULL) != 0;
        }
        finally {
          unref.invokeExact(notification);
        }
      }
      catch (Throwable error) {
        throw new IllegalStateException(error);
      }
    }
  }

  private final LibNotify myLibNotify;
  private final String myIcon;
  private final Object myLock = new Object();
  private boolean myDisposed = false;

  private LibNotifyWrapper() {
    myLibNotify = new LibNotify(SymbolLookup.libraryLookup("libnotify.so.4", Arena.global()));

    var appName = ApplicationNamesInfo.getInstance().getProductName();
    myLibNotify.init(appName);

    var icon = AppUIUtil.findAppIcon();
    myIcon = icon != null ? icon : "dialog-information";

    var connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
      @Override
      public void appClosing() {
        synchronized (myLock) {
          myDisposed = true;
          myLibNotify.uninit();
        }
      }
    });
  }

  @Override
  public void notify(@NotNull String name, @NotNull String title, @NotNull String description) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      synchronized (myLock) {
        if (!myDisposed) {
          myLibNotify.notify(title, description, myIcon);
        }
      }
    });
  }
}
