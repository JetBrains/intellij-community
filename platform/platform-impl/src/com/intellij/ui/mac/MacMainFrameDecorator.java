/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.mac;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.util.Function;
import com.sun.jna.Callback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.ui.mac.foundation.Foundation.invoke;
import static com.intellij.ui.mac.foundation.Foundation.toStringViaUTF8;

/**
 * User: spLeaner
 */
public class MacMainFrameDecorator implements UISettingsListener, Disposable {
  private static boolean SHOWN = false;

  private static Callback SET_VISIBLE_CALLBACK = new Callback() {
    public void callback(ID caller, ID selector, ID value) {
      SHOWN = value.intValue() == 1;
      SwingUtilities.invokeLater(CURRENT_SETTER);
    }
  };

  private static Callback IS_VISIBLE = new Callback() {
    public boolean callback(ID caller) {
      return SHOWN;
    }
  };

  private static AtomicInteger UNIQUE_COUNTER = new AtomicInteger(0);

  public static final Runnable TOOLBAR_SETTER = new Runnable() {
    @Override
    public void run() {
      final UISettings settings = UISettings.getInstance();
      settings.SHOW_MAIN_TOOLBAR = SHOWN;
      settings.fireUISettingsChanged();
    }
  };

  public static final Runnable NAVBAR_SETTER = new Runnable() {
    @Override
    public void run() {
      final UISettings settings = UISettings.getInstance();
      settings.SHOW_NAVIGATION_BAR = SHOWN;
      settings.fireUISettingsChanged();
    }
  };

  public static final Function<Object, Boolean> NAVBAR_GETTER = new Function<Object, Boolean>() {
    @Override
    public Boolean fun(Object o) {
      return UISettings.getInstance().SHOW_NAVIGATION_BAR;
    }
  };

  public static final Function<Object, Boolean> TOOLBAR_GETTER = new Function<Object, Boolean>() {
    @Override
    public Boolean fun(Object o) {
      return UISettings.getInstance().SHOW_MAIN_TOOLBAR;
    }
  };

  private static Runnable CURRENT_SETTER = null;
  private static Function<Object, Boolean> CURRENT_GETTER = null;
  private String myClassName;

  public MacMainFrameDecorator(@NotNull final Frame frame, final boolean navBar) {
    final ID window = findWindowForTitle(frame.getTitle());
    if (window == null) return;

    if (CURRENT_SETTER == null) {
      CURRENT_SETTER = navBar ? NAVBAR_SETTER : TOOLBAR_SETTER;
      CURRENT_GETTER = navBar ? NAVBAR_GETTER : TOOLBAR_GETTER;
      SHOWN = CURRENT_GETTER.fun(null);
    }

    UISettings.getInstance().addUISettingsListener(this, this);

    final ID pool = invoke("NSAutoreleasePool", "new");

    try {
      myClassName = "IdeaToolbar" + UNIQUE_COUNTER.incrementAndGet();

      final ID ownToolbar = Foundation.registerObjcClass(Foundation.getClass("NSToolbar"), myClassName);
      Foundation.registerObjcClassPair(ownToolbar);

      ID toolbar = invoke(invoke(myClassName, "alloc"), "initWithIdentifier:", Foundation.cfString(myClassName));
      Foundation.cfRetain(toolbar);

      invoke(toolbar, "setVisible:", 0); // hide native toolbar by default

      Foundation.addMethod(ownToolbar, Foundation.createSelector("setVisible:"), SET_VISIBLE_CALLBACK, "v*");
      Foundation.addMethod(ownToolbar, Foundation.createSelector("isVisible"), IS_VISIBLE, "B*");

      invoke(window, "setToolbar:", toolbar);
      invoke(window, "setShowsToolbarButton:", 1);
    }
    finally {
      invoke(pool, "release");
    }
  }

  public void remove() {
    // TODO: clean up?
    Disposer.dispose(this);
  }

  @Override
  public void uiSettingsChanged(final UISettings source) {
    if (CURRENT_GETTER != null) {
      SHOWN = CURRENT_GETTER.fun(null);
    }
  }

  @Override
  public void dispose() {
  }

  @Nullable
  public static ID findWindowForTitle(final String title) {
    if (title == null || title.length() == 0) return null;
    final ID pool = invoke("NSAutoreleasePool", "new");

    ID focusedWindow = null;
    try {
      final ID sharedApplication = invoke("NSApplication", "sharedApplication");
      final ID windows = invoke(sharedApplication, "windows");
      final ID windowEnumerator = invoke(windows, "objectEnumerator");

      while (true) {
        // dirty hack: walks through all the windows to find a cocoa window to show sheet for
        final ID window = invoke(windowEnumerator, "nextObject");
        if (0 == window.intValue()) break;

        final ID windowTitle = invoke(window, "title");
        if (windowTitle != null && windowTitle.intValue() != 0) {
          final String titleString = toStringViaUTF8(windowTitle);
          if (titleString.equals(title)) {
            if (1 == invoke(window, "isVisible").intValue()) {
              focusedWindow = window;
              break;
            }
          }
        }
      }
    }
    finally {
      invoke(pool, "release");
    }

    return focusedWindow;
  }
}
