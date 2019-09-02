// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.io;

import com.intellij.ide.PowerSaveMode;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.loader.NativeLibraryLoader;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

public final class PowerSupplyKit {
  private static final String POWER_SUPPLY_GROUP_ID = "Power Supply Integration";
  private static boolean shouldCheckCordUnplug = SystemInfo.isMacIntel64 && Registry.is("check.power.supply.for.mbp");
  //private static boolean shouldCheckDiscreteCard =
  // Registry.is("check.power.supply.for.mbp") && SystemInfo.isMacIntel64 && hasDiscreteCard();

  static {
    if (shouldCheckCordUnplug) {
      NativeLibraryLoader.loadPlatformLibrary("MacNativeKit");
    }
  }

  private static native void startListenPowerSupply(PowerSupplyKitCallback callback);

  private static native boolean isPlugged();

  private static native String[] getInfo();

  private static boolean automaticallySwitchInPowerSaveModeOnUnpluggedCordEvent;

  // The first notification has been shown and
  // power callback has been configurated
  private static boolean powerSupplyKitHasBeenInitialized;

  private static boolean hasDiscreteCard() {
    String[] models = getInfo();

    if (models.length > 1) {
      return true;
      //for (String model : models) {
      //    if (model.contains("NVIDIA")) return true;
      //}
    }

    return false;
  }

  public static void checkPowerSupply() {
    if (!shouldCheckCordUnplug) {
      return;
    }

    new Thread(() -> {
      startListenPowerSupply(new PowerSupplyKitCallback() {
        @Override
        public void call() {
          initializeIfNeeded();
          PowerSaveMode.setEnabled(automaticallySwitchInPowerSaveModeOnUnpluggedCordEvent && isPlugged());
        }
      });
    }, "check power").start();
    shouldCheckCordUnplug = false;
  }

  private static void initializeIfNeeded() {
    if (powerSupplyKitHasBeenInitialized) return;

    NotificationsConfiguration.getNotificationsConfiguration().register(
      POWER_SUPPLY_GROUP_ID,
      NotificationDisplayType.STICKY_BALLOON,
      false);

    final NotificationType type = NotificationType.INFORMATION;

    final NotificationListener listener = new NotificationListener() {
      @Override
      public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          final String description = event.getDescription();
          if ("doNotShow".equals(description)) {
            NotificationsConfiguration.getNotificationsConfiguration()
              .changeSettings(POWER_SUPPLY_GROUP_ID, NotificationDisplayType.NONE, false, false);
            notification.expire();
          }
          if ("automatically".equals(description)) {
            automaticallySwitchInPowerSaveModeOnUnpluggedCordEvent = true;
          }
        }
      }
    };

    //final String message =  "We have noticed that your computer power cord is disconnected and you are using" +
    //                        " a discrete video card on you MacBook Pro. You can switch " +
    //                        " to the integrated video card. This significantly extend your battery life." +
    //                        " <a href=\"doNotShow\">Do no show</a> this message anymore";

    final String automaticPowerSafeModeSwitchMessage = "We have noticed that your computer power cord is disconnected. " +
                                                       "On unplugging your power cord we can <a href=\"automatically\">automatically</a> switch " +
                                                       "your Mac Book in a <b>Power Save</b> mode . This can extend your battery life." +
                                                       " <a href=\"doNotShow\">Do no show</a> this message anymore";

    //final String powerSafeModeTurnedOnMesssage =  "Power Save mode is turned on. <a href=\"doNotShow\">Do no show</a> this message anymore";

    //final Notification notification = new Notification(POWER_SUPPLY_GROUP_ID, "Discrete video card warning", message, type, listener);
    final Notification automaticPowerSafeModeSwitchNotification =
      new Notification(POWER_SUPPLY_GROUP_ID, "Automatically enable <b>Power Save</b> mode",
                       automaticPowerSafeModeSwitchMessage, type, listener);


    //final Notification powerSafeModeSwitchedNotification =
    //  new Notification(POWER_SUPPLY_GROUP_ID, "\"Power Save\" mode ", message, type, listener);

    ApplicationManager.getApplication().executeOnPooledThread(() -> ApplicationManager.getApplication().getMessageBus().
      syncPublisher(Notifications.TOPIC).notify(automaticPowerSafeModeSwitchNotification));

    powerSupplyKitHasBeenInitialized = true;
  }
}
