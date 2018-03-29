// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.execution.*;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.ExecUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.util.lang.UrlClassLoader;
import com.sun.jna.Native;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TouchBarManager {
  private static final Logger ourLog = Logger.getInstance(TouchBar.class);

  private static final String ourTBServerProcessName = "TouchBarServer";
  private static final String ourDefaultsDomain = "com.apple.touchbar.agent";
  private static final String ourDefaultsNode = "PresentationModePerApp";
  private static final String ourDefaultsValue = "functionKeys";
  private static final String ourApplicationId;
  private static final NSTLibrary ourNSTLibrary;
  private static TouchBar ourTouchbar;

  static {
    // NOTE: can also check existence of process 'ControlStrip' to determine touchbar availability
    final boolean isSystemSupportTouchbar = SystemInfo.isMac && SystemInfo.isOsVersionAtLeast("10.12.1");
    NSTLibrary lib = null;
    if (isSystemSupportTouchbar && Registry.is("ide.mac.touchbar.use", false) && isTouchBarServerRunning()) {
      try {
        UrlClassLoader.loadPlatformLibrary("nst");

        // Set JNA to convert java.lang.String to char* using UTF-8, and match that with
        // the way we tell CF to interpret our char*
        // May be removed if we use toStringViaUTF16
        System.setProperty("jna.encoding", "UTF8");

        final Map<String, Object> nstOptions = new HashMap<>();
        lib = Native.loadLibrary("nst", NSTLibrary.class, nstOptions);
      } catch (Throwable e) {
        ourLog.error("Failed to load nst library for touchbar: ", e);
      }
    }
    ourNSTLibrary = lib;

    // TODO: obtain "OS X Application identifier' via platform api
    ourApplicationId = ApplicationInfoImpl.getInstanceEx().isEAP() ? "com.jetbrains.intellij-EAP" : "com.jetbrains.intellij";
  }

  public enum TOUCHBARS {
    main,
    debug,
    test; /*only for testing purposes*/

    private TouchBar myTB = null;

    TouchBar get(Project project) {
      if (myTB == null) {
        final ID pool = Foundation.invoke("NSAutoreleasePool", "new");
        try {
          if (this == main) {
            if (project == null) {
              ourLog.error("can't create 'main' touchbar: passed null project");
              return null;
            }

            myTB = new TouchBar(name());
            myTB.addButton(AllIcons.Toolwindows.ToolWindowBuild, null, "CompileDirty"); // NOTE: IdeActions.ACTION_COMPILE doesn't work

            final RunManager rm = RunManager.getInstance(project);
            final RunnerAndConfigurationSettings selected = rm.getSelectedConfiguration();
            if (selected != null) {
              final TouchBar tapHoldTB = new TouchBar("run_configs_popover_tap_and_hold");
              final TouchBar expandTB = new TouchBar("run_configs_popover_expand");
              final TBItemPopover popover = myTB.addPopover(selected.getConfiguration().getIcon(), selected.getName(), 143, expandTB, tapHoldTB);

              tapHoldTB.addFlexibleSpacing();
              tapHoldTB.addButton(selected.getConfiguration().getIcon(), selected.getName(), () -> {}); // TODO: remove button, use NSView with 'image+text'
              tapHoldTB.selectAllItemsToShow();

              expandTB.addButton(AllIcons.Actions.EditSource, null, IdeActions.ACTION_EDIT_RUN_CONFIGURATIONS);
              final TBItemScrubber scrubber = expandTB.addScrubber(500);
              List<RunnerAndConfigurationSettings> allRunCongigs = rm.getAllSettings();
              for (RunnerAndConfigurationSettings rc : allRunCongigs) {
                final Icon iconRc = rc.getConfiguration().getIcon();
                scrubber.addItem(iconRc, rc.getName(), () -> {
                  rm.setSelectedConfiguration(rc);
                  popover.update(iconRc, rc.getName());
                  popover.dismiss();
                });
              }
              expandTB.addFlexibleSpacing();
              expandTB.selectAllItemsToShow();

              myTB.addButton(AllIcons.Toolwindows.ToolWindowRun, null, IdeActions.ACTION_DEFAULT_RUNNER);
              myTB.addButton(AllIcons.Toolwindows.ToolWindowDebugger, null, IdeActions.ACTION_DEFAULT_DEBUGGER);
              myTB.addButton(IconLoader.getDisabledIcon(AllIcons.Actions.Suspend), null, (NSTLibrary.Action)null);
            }

            myTB.addSpacing(true);
            myTB.addButton(AllIcons.Actions.CheckOut, null, new PlatformAction("Vcs.UpdateProject"));  // NOTE: IdeActions.ACTION_CVS_CHECKOUT doesn't works
            myTB.addButton(AllIcons.Actions.Commit, null, new PlatformAction("CheckinProject"));       // NOTE: IdeActions.ACTION_CVS_COMMIT doesn't works
          }
          else if (this == debug) {
            myTB = new TouchBar(name());
            myTB.addButton(null, "Step Over", "Step Over");
            myTB.addButton(null, "Step Into", "Step Into");
            myTB.addButton(AllIcons.Actions.Suspend, null, "Stop");
          }
          else if (this == test) {
            myTB = new TouchBar(name());
            myTB.addSpacing(true);
            myTB.addButton(null, "test1", createPrintTextCallback("pressed test1 button"));
            myTB.addButton(null, "test2", createPrintTextCallback("pressed test2 button"));
            myTB.addSpacing(false);
            myTB.addButton(AllIcons.Toolwindows.ToolWindowRun, null, createPrintTextCallback("pressed image button"));


            final TouchBar tapHoldTB = new TouchBar("test_popover_tap_and_hold");
            final TouchBar expandTB = new TouchBar("test_configs_popover_expand");
            final int configPopoverWidth = 143;
            myTB.addPopover(AllIcons.Toolwindows.ToolWindowBuild, "test-popover", configPopoverWidth, expandTB, tapHoldTB);

            expandTB.addButton(AllIcons.Toolwindows.ToolWindowDebugger, null, createPrintTextCallback("pressed pimage button"));
            final TBItemScrubber scrubber = expandTB.addScrubber(450);
            for (int c = 0; c < 15; ++c) {
              String txt;
              if (c == 7)           txt = "very very long configuration name (debugging type)";
              else                  txt = String.format("r%1.2f", Math.random());
              int finalC = c;
              scrubber.addItem(AllIcons.Toolwindows.ToolWindowPalette, txt,
                               () -> System.out.println("JAVA: performed action of scrubber item at index " + finalC + " [thread:" + Thread.currentThread() + "]"));
            }
            expandTB.selectAllItemsToShow();

            tapHoldTB.addButton(AllIcons.Toolwindows.ToolWindowPalette, null, createPrintTextCallback("pressed pimage button"));
            tapHoldTB.selectAllItemsToShow();
          }

          if (myTB != null)
            myTB.selectAllItemsToShow();
        }
        finally {
          Foundation.invoke(pool, "release");
        }
      }
      return myTB;
    }

    void release() {
      if (myTB == null)
        return;

      myTB.release();
      myTB = null;
    }
  }

  public static void initialize() {
    if (!isTouchBarAvailable())
      return;

    final ID app = Foundation.invoke("NSApplication", "sharedApplication");
    Foundation.invoke(app, "setAutomaticCustomizeTouchBarMenuItemEnabled:", true);

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(Project project) {
        ourTouchbar = TOUCHBARS.main.get(project);
        if (ourTouchbar == null)
          return;

        ourNSTLibrary.setTouchBar(ourTouchbar.getNativePeer());
      }
    });
  }

  public static void setCurrent(TOUCHBARS tbType) {
    if (tbType == null || !isTouchBarAvailable())
      return;

    ourTouchbar = tbType.get(null);
    if (ourTouchbar == null)
      return;

    ourNSTLibrary.setTouchBar(ourTouchbar.getNativePeer());
  }

  static NSTLibrary getNSTLibrary() { return ourNSTLibrary; }

  public static boolean isTouchBarAvailable() { return ourNSTLibrary != null; }

  private static boolean isTouchBarServerRunning() {
    final GeneralCommandLine cmdLine = new GeneralCommandLine("pgrep", ourTBServerProcessName);
    try {
      final ProcessOutput out = ExecUtil.execAndGetOutput(cmdLine);
      return !out.getStdout().isEmpty();
    } catch (ExecutionException e) {
      ourLog.error(e);
    }
    return false;
  }

  public static boolean isShowFnKeysEnabled() {
    final ID defaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults");
    final ID domain = Foundation.invoke(defaults, "persistentDomainForName:", Foundation.nsString(ourDefaultsDomain));
    final ID node = Foundation.invoke(domain, "objectForKey:", Foundation.nsString(ourDefaultsNode));
    final ID val = Foundation.invoke(node, "objectForKey:", Foundation.nsString(ourApplicationId));
    final String sval = Foundation.toStringViaUTF8(val);
    return sval != null && sval.equals(ourDefaultsValue);
  }

  public static void setShowFnKeysEnabled(boolean val) {
    final ID defaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults");
    final ID domain = Foundation.invoke(defaults, "persistentDomainForName:", Foundation.nsString(ourDefaultsDomain));
    final ID node = Foundation.invoke(domain, "objectForKey:", Foundation.nsString(ourDefaultsNode));
    final ID nsVal = Foundation.invoke(node, "objectForKey:", Foundation.nsString(ourApplicationId));
    final String sval = Foundation.toStringViaUTF8(nsVal);
    final boolean settingEnabled = sval != null && sval.equals(ourDefaultsValue);
    if (val == settingEnabled)
      return;

    final ID mdomain = Foundation.invoke(domain, "mutableCopy");
    final ID mnode = Foundation.invoke(node, "mutableCopy");
    if (val)
      Foundation.invoke(mnode, "setObject:forKey:", Foundation.nsString(ourDefaultsValue), Foundation.nsString(ourApplicationId));
    else
      Foundation.invoke(mnode, "removeObjectForKey:", Foundation.nsString(ourApplicationId));
    Foundation.invoke(mdomain, "setObject:forKey:", mnode, Foundation.nsString(ourDefaultsNode));
    Foundation.invoke(defaults, "setPersistentDomain:forName:", mdomain, Foundation.nsString(ourDefaultsDomain));

    try {
      ExecUtil.sudo(new GeneralCommandLine("pkill", ourTBServerProcessName), "");
    } catch (ExecutionException e) {
      ourLog.error(e);
    } catch (IOException e) {
      ourLog.error(e);
    }
  }

  private static NSTLibrary.Action createPrintTextCallback(String text) {
    return new NSTLibrary.Action() {
      @Override
      public void execute() {
        System.out.println(text + " [thread:" + Thread.currentThread() + "]");
      }
    };
  }
}
