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
import java.util.ArrayList;
import java.util.List;

public class SystemSettingsWrapper {
  private static final Logger LOG = Logger.getInstance(SystemSettingsWrapper.class);

  private static final String ourDefaultsDomain = "com.apple.touchbar.agent";
  private static final String ourDefaultsNode = "PresentationModePerApp";
  private static final String ourDefaultsValue = "functionKeys";

  private static SystemSettingsWrapper ourInstance;

  private static final String TB_SERVER_PROCESS = SystemInfo.isMacOSHighSierra ? "TouchBarServer" : "TouchBarAgent";

  private final String myAppId;
  private ID myDefaults = ID.NIL;
  private ID myDomain = ID.NIL;
  private ID myNode = ID.NIL;
  private ID myNsVal = ID.NIL;
  private String mySval = null;

  SystemSettingsWrapper(String appId) {
    myAppId = appId;
    readDefaults();
  }

  public static SystemSettingsWrapper getInstance() {
    if (ourInstance == null)
      ourInstance = new SystemSettingsWrapper(_getAppId());
    return ourInstance;
  }

  public boolean isShowFnKeysEnabled() { return isAppRecordExists() ? mySval.equals(ourDefaultsValue) : false; }

  public void setShowFnKeysEnabled(boolean val, boolean doRestartServer/*can be false only for tests*/) {
    if (!isSettingsNodeExists())
      return;

    final boolean settingEnabled = isShowFnKeysEnabled();
    if (val == settingEnabled)
      return;

    final ID mdomain = Foundation.invoke(myDomain, "mutableCopy");
    final ID mnode = Foundation.invoke(myNode, "mutableCopy");
    if (val)
      Foundation.invoke(mnode, "setObject:forKey:", Foundation.nsString(ourDefaultsValue), Foundation.nsString(myAppId));
    else
      Foundation.invoke(mnode, "removeObjectForKey:", Foundation.nsString(myAppId));
    Foundation.invoke(mdomain, "setObject:forKey:", mnode, Foundation.nsString(ourDefaultsNode));
    Foundation.invoke(myDefaults, "setPersistentDomain:forName:", mdomain, Foundation.nsString(ourDefaultsDomain));

    readDefaults();
    if (doRestartServer)
      restartServer();
  }

  public String toString() {
    String res = "";
    if (myDefaults != null) res += "Default="+myDefaults+";";
    if (myDomain != null) res += "Domain="+myDomain+";";
    if (myNode != null) res += "Node="+myNode+";";
    if (myNsVal != null) res += "Nsval="+myNsVal+";";
    if (mySval != null) res += "Sval="+mySval+";";
    return res;
  }

  boolean isSettingsDomainExists() {
    return myDefaults != null && myDomain != null && !myDefaults.equals(ID.NIL) && !myDomain.equals(ID.NIL);
  }

  boolean isSettingsNodeExists() {
    return
      myDefaults != null && myDomain != null && myNode != null
      && !myDefaults.equals(ID.NIL) && !myDomain.equals(ID.NIL) && !myNode.equals(ID.NIL);
  }

  boolean isAppRecordExists() { return isSettingsNodeExists() && myNsVal != ID.NIL && mySval != null && !mySval.isEmpty(); }

  void readDefaults() {
    myDefaults = ID.NIL;
    myDomain = ID.NIL;
    myNode = ID.NIL;
    myNsVal = ID.NIL;
    mySval = null;
    try {
      _readNodes();
      _readValues();
    } catch (RuntimeException e) {
      LOG.error("can't read touchbar settings", e);
    }
  }

  static boolean isTouchBarServerRunning() {
    final GeneralCommandLine cmdLine = new GeneralCommandLine("pgrep", TB_SERVER_PROCESS);
    try {
      final ProcessOutput out = ExecUtil.execAndGetOutput(cmdLine);
      return !out.getStdout().isEmpty();
    } catch (ExecutionException e) {
      LOG.error(e);
    }
    return false;
  }

  static void restartServer() {
    try {
      ExecUtil.sudo(new GeneralCommandLine("pkill", TB_SERVER_PROCESS), "");
    } catch (ExecutionException e) {
      LOG.error(e);
    } catch (IOException e) {
      LOG.error(e);
    }
  }

  private void _readNodes() {
    myDefaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults");
    if (myDefaults == null || myDefaults.equals(ID.NIL))
      return;

    // NOTE: skip Foundation.invoke(myDefaults, "synchronize");
    // It waits for any pending asynchronous updates to the defaults database and returns; this method is unnecessary and shouldn't be used.

    myDomain = Foundation.invoke(myDefaults, "persistentDomainForName:", Foundation.nsString(ourDefaultsDomain));
    if (myDomain == null || myDomain.equals(ID.NIL))
      return;
    myNode = Foundation.invoke(myDomain, "objectForKey:", Foundation.nsString(ourDefaultsNode));
  }

  private void _readValues() {
    if (myNode == null || myNode.equals(ID.NIL))
      return;
    myNsVal = Foundation.invoke(myNode, "objectForKey:", Foundation.nsString(myAppId));
    mySval = Foundation.toStringViaUTF8(myNsVal);
  }

  private static String _getAppId() {
    // TODO: obtain "OS X Application identifier' via platform api
    return ApplicationInfoImpl.getInstanceEx().isEAP() ? "com.jetbrains.intellij-EAP" : "com.jetbrains.intellij";
  }

  private static List<String> _listAllDomains(ID nsDefaults) {
    List<String> res = new ArrayList<>(100);
    final ID allKeysDict = Foundation.invoke(nsDefaults, "dictionaryRepresentation");
    final ID allKeysArr = Foundation.invoke(allKeysDict, "allKeys");
    final ID count = Foundation.invoke(allKeysArr, "count");
    for (int c = 0; c < count.intValue(); ++c) {
      final ID nsKeyName = Foundation.invoke(allKeysArr, "objectAtIndex:", c);
      final String keyName = Foundation.toStringViaUTF8(nsKeyName);
      // System.out.println(keyName);
      res.add(keyName);
    }
    return res;
  }
}
