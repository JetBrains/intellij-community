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

import com.apple.eawt.AppEvent;
import com.apple.eawt.FullScreenAdapter;
import com.apple.eawt.FullScreenUtilities;
import com.intellij.Patches;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.Function;
import com.sun.jna.Callback;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.ui.mac.foundation.Foundation.invoke;

/**
 * User: spLeaner
 */
public class MacMainFrameDecorator implements UISettingsListener, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.mac.MacMainFrameDecorator");
  
  public static final Key<Boolean> SHOULD_OPEN_IN_FULLSCREEN = Key.create("mac.should.open.in.fullscreen");
  
  public static final String FULL_SCREEN = "Idea.Is.In.FullScreen.Mode.Now";
  private static boolean HAS_FULLSCREEN_UTILITIES;
  static {
    try {
      Class.forName("com.apple.eawt.FullScreenUtilities");
      HAS_FULLSCREEN_UTILITIES = true;
    } catch (Exception e) {
      HAS_FULLSCREEN_UTILITIES = false;
    }
  }
  public static final boolean FULL_SCREEN_AVAILABLE = SystemInfo.isJavaVersionAtLeast("1.6.0_29") && HAS_FULLSCREEN_UTILITIES;
  
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

  private boolean myInFullScreen;
  private IdeFrameImpl myFrame;

  public MacMainFrameDecorator(@NotNull final IdeFrameImpl frame, final boolean navBar) {
    myFrame = frame;

    final ID window = MacUtil.findWindowForTitle(frame.getTitle());
    if (window == null) return;

    if (CURRENT_SETTER == null) {
      CURRENT_SETTER = navBar ? NAVBAR_SETTER : TOOLBAR_SETTER;
      CURRENT_GETTER = navBar ? NAVBAR_GETTER : TOOLBAR_GETTER;
      SHOWN = CURRENT_GETTER.fun(null);
    }

    UISettings.getInstance().addUISettingsListener(this, this);

    final ID pool = invoke("NSAutoreleasePool", "new");

    int v = UNIQUE_COUNTER.incrementAndGet();
    if (Patches.APPLE_BUG_ID_10514018) {
      frame.addWindowListener(new WindowAdapter() {
        @Override
        public void windowDeiconified(WindowEvent e) {
          if (e.getWindow() == frame && frame.getState() == Frame.ICONIFIED) {
            frame.setState(Frame.NORMAL);
          }
        }
      });
    }
    try {
      if (SystemInfo.isMacOSLion) {
        if (!FULL_SCREEN_AVAILABLE) return;

        FullScreenUtilities.addFullScreenListenerTo(frame, new FullScreenAdapter() {
          @Override
          public void windowEnteredFullScreen(AppEvent.FullScreenEvent event) {
            myInFullScreen = true;
            frame.storeFullScreenStateIfNeeded(true);

            JRootPane rootPane = frame.getRootPane();
            if (rootPane != null) rootPane.putClientProperty(FULL_SCREEN, Boolean.TRUE);
            if (Patches.APPLE_BUG_ID_10207064) {
              // fix problem with bottom empty bar
              // it seems like the title is still visible in fullscreen but the window itself shifted up for titlebar height
              // and the size of the frame is still calculated to be the height of the screen which is wrong
              // so just add these titlebar height to the frame height once again
              Timer timer = new Timer(300, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                  SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                      frame.setSize(frame.getWidth(), frame.getHeight() + frame.getInsets().top);
                    }
                  });
                }
              });
              timer.setRepeats(false);
              timer.start();
            }
          }

          @Override
          public void windowExitedFullScreen(AppEvent.FullScreenEvent event) {
            myInFullScreen = false;
            frame.storeFullScreenStateIfNeeded(false);

            JRootPane rootPane = frame.getRootPane();
            if (rootPane != null) rootPane.putClientProperty(FULL_SCREEN, null);
          }
        });
      } else {
        // toggle toolbar
        String className = "IdeaToolbar" + v;
        final ID ownToolbar = Foundation.allocateObjcClassPair(Foundation.getObjcClass("NSToolbar"), className);
        Foundation.registerObjcClassPair(ownToolbar);

        ID toolbar = invoke(invoke(className, "alloc"), "initWithIdentifier:", Foundation.nsString(className));
        Foundation.cfRetain(toolbar);

        invoke(toolbar, "setVisible:", 0); // hide native toolbar by default

        Foundation.addMethod(ownToolbar, Foundation.createSelector("setVisible:"), SET_VISIBLE_CALLBACK, "v*");
        Foundation.addMethod(ownToolbar, Foundation.createSelector("isVisible"), IS_VISIBLE, "B*");

        invoke(window, "setToolbar:", toolbar);
        invoke(window, "setShowsToolbarButton:", 1);
      }
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
    myFrame = null;
  }

  public void toggleFullScreen() {
    toggleFullScreen(!isInFullScreen());
  }

  public boolean isInFullScreen() {
    return myInFullScreen;
  }

  public void toggleFullScreen(boolean state) {
    if (!SystemInfo.isMacOSLion || myFrame == null) return;
    if (myInFullScreen != state) {
      final ID window = MacUtil.findWindowForTitle(myFrame.getTitle());
      if (window == null) return;
      invoke(window, "toggleFullScreen:", window);
    }
  }
}
