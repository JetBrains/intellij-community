// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;

import java.io.IOException;

public class SystemSettingsTouchBar {
  private static final Logger LOG = Logger.getInstance(SystemSettingsTouchBar.class);

  private static final String ourTBServerProcessName = SystemInfo.isMacOSHighSierra ? "TouchBarServer" : "TouchBarAgent";
  private static final String ourDefaultsDomain = "com.apple.touchbar.agent";
  private static final String ourDefaultsNode = "PresentationModePerApp";
  private static final String ourDefaultsValue = "functionKeys";

  static boolean isTouchBarServerRunning() {
    final GeneralCommandLine cmdLine = new GeneralCommandLine("pgrep", ourTBServerProcessName);
    try {
      final ProcessOutput out = ExecUtil.execAndGetOutput(cmdLine);
      return !out.getStdout().isEmpty();
    } catch (ExecutionException e) {
      LOG.error(e);
    }
    return false;
  }

  public static boolean isShowFnKeysEnabled() {
    final ID defaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults");
    final ID domain = Foundation.invoke(defaults, "persistentDomainForName:", Foundation.nsString(ourDefaultsDomain));
    final ID node = Foundation.invoke(domain, "objectForKey:", Foundation.nsString(ourDefaultsNode));
    final ID val = Foundation.invoke(node, "objectForKey:", Foundation.nsString(_getAppId()));
    final String sval = Foundation.toStringViaUTF8(val);
    return sval != null && sval.equals(ourDefaultsValue);
  }

  public static void setShowFnKeysEnabled(boolean val) {
    final ID defaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults");
    final ID domain = Foundation.invoke(defaults, "persistentDomainForName:", Foundation.nsString(ourDefaultsDomain));
    final ID node = Foundation.invoke(domain, "objectForKey:", Foundation.nsString(ourDefaultsNode));
    final ID nsVal = Foundation.invoke(node, "objectForKey:", Foundation.nsString(_getAppId()));
    final String sval = Foundation.toStringViaUTF8(nsVal);
    final boolean settingEnabled = sval != null && sval.equals(ourDefaultsValue);
    if (val == settingEnabled)
      return;

    final ID mdomain = Foundation.invoke(domain, "mutableCopy");
    final ID mnode = Foundation.invoke(node, "mutableCopy");
    if (val)
      Foundation.invoke(mnode, "setObject:forKey:", Foundation.nsString(ourDefaultsValue), Foundation.nsString(_getAppId()));
    else
      Foundation.invoke(mnode, "removeObjectForKey:", Foundation.nsString(_getAppId()));
    Foundation.invoke(mdomain, "setObject:forKey:", mnode, Foundation.nsString(ourDefaultsNode));
    Foundation.invoke(defaults, "setPersistentDomain:forName:", mdomain, Foundation.nsString(ourDefaultsDomain));

    try {
      ExecUtil.sudo(new GeneralCommandLine("pkill", ourTBServerProcessName), "");
    } catch (ExecutionException e) {
      LOG.error(e);
    } catch (IOException e) {
      LOG.error(e);
    }
  }

  private static String _getAppId() {
    // TODO: obtain "OS X Application identifier' via platform api
    return ApplicationInfoImpl.getInstanceEx().isEAP() ? "com.jetbrains.intellij-EAP" : "com.jetbrains.intellij";
  }
}
