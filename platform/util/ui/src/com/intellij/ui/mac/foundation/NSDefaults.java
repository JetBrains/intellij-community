// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.foundation;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class NSDefaults {
  private static final Logger LOG = Logger.getInstance(NSDefaults.class);

  // NOTE: skip call of Foundation.invoke(myDefaults, "synchronize") (when read settings)
  // It waits for any pending asynchronous updates to the defaults database and returns; this method is unnecessary and shouldn't be used.

  private static final class Path {
    private final @NotNull ArrayList<Node> myPath = new ArrayList<>();

    @Override
    public String toString() {
      return myPath.stream().map(Node::toString).collect(Collectors.joining(" | "));
    }

    String readStringVal(@NotNull String key) { return readStringVal(key, false); }

    String readStringVal(@NotNull String key, boolean doSynchronize) {
      if (myPath.isEmpty())
        return null;

      final Foundation.NSAutoreleasePool pool = new Foundation.NSAutoreleasePool();
      try {
        final ID defaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults");
        if (defaults.equals(ID.NIL))
          return null;

        if (doSynchronize) {
          // NOTE: AppleDoc proposes to skip call of Foundation.invoke(myDefaults, "synchronize") - "this method is unnecessary and shouldn't be used."
          Foundation.invoke(defaults, "synchronize");
        }

        _readPath(defaults);
        final Node tail = myPath.get(myPath.size() - 1);
        if (!tail.isValid())
          return null;

        final ID valObj = Foundation.invoke(tail.cachedNodeObj, "objectForKey:", Foundation.nsString(key));
        if (valObj.equals(ID.NIL))
          return null;
        return Foundation.toStringViaUTF8(valObj);
      } finally {
        pool.drain();
        _resetPathCache();
      }
    }

    void writeStringValue(@NotNull String key, String val) {
      if (myPath.isEmpty())
        return;

      final Foundation.NSAutoreleasePool pool = new Foundation.NSAutoreleasePool();
      try {
        final ID defaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults");
        if (defaults.equals(ID.NIL))
          return;

        _readPath(defaults);

        int pos = myPath.size() - 1;
        Node child = myPath.get(pos--);
        if (!child.isValid()) {
          if (val == null) // nothing to erase
            return;
        }

        child.writeStringValue(key, val);
        while (pos >= 0) {
          final Node parent = myPath.get(pos--);
          final ID mnode = Foundation.invoke(parent.cachedNodeObj, "mutableCopy");
          Foundation.invoke(mnode, "setObject:forKey:", child.cachedNodeObj, Foundation.nsString(child.myNodeName));
          parent.cachedNodeObj = mnode;
          child = parent;
        }

        final String topWriteSelector = child.isDomain() ? "setPersistentDomain:forName:" : "setObject:forKey:";
        Foundation.invoke(defaults, topWriteSelector, child.cachedNodeObj, Foundation.nsString(child.myNodeName));
      } finally {
        pool.drain();
        _resetPathCache();
      }
    }

    int lastValidPos() {
      if (myPath.isEmpty())
        return -1;

      final Foundation.NSAutoreleasePool pool = new Foundation.NSAutoreleasePool();
      try {
        final ID defaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults");
        if (defaults.equals(ID.NIL))
          return -1;

        _readPath(defaults);

        for (int pos = 0; pos < myPath.size(); ++pos) {
          final Node pn = myPath.get(pos);
          if (!pn.isValid())
            return pos - 1;
        }
        return myPath.size() - 1;
      } finally {
        pool.drain();
        _resetPathCache();
      }
    }

    private static final class Node {
      private final @NotNull String mySelector;
      private final @NotNull String myNodeName;
      private @NotNull ID cachedNodeObj = ID.NIL;

      Node(@NotNull String selector, @NotNull String nodeName) {
        mySelector = selector;
        myNodeName = nodeName;
      }

      @Override
      public String toString() { return String.format("sel='%s' nodeName='%s'",mySelector, myNodeName); }

      boolean isValid() { return !cachedNodeObj.equals(ID.NIL); }

      boolean isDomain() { return mySelector.equals("persistentDomainForName:"); }

      void readNode(ID parent) {
        cachedNodeObj = ID.NIL;

        if (parent == null || parent.equals(ID.NIL))
          return;

        final ID nodeObj = Foundation.invoke(parent, mySelector, Foundation.nsString(myNodeName));
        if (nodeObj.equals(ID.NIL))
          return;

        cachedNodeObj = nodeObj;
      }

      private static @NotNull ID _createDictionary() { return Foundation.invoke("NSMutableDictionary", "new"); }

      void writeStringValue(@NotNull String key, String val) {
        final ID mnode;
        if (!isValid()) {
          if (val == null) // nothing to erase
            return;

          mnode = _createDictionary();
        } else
          mnode = Foundation.invoke(cachedNodeObj, "mutableCopy");

        if (mnode.equals(ID.NIL))
          return;

        if (val != null)
          Foundation.invoke(mnode, "setObject:forKey:", Foundation.nsString(val), Foundation.nsString(key));
        else
          Foundation.invoke(mnode, "removeObjectForKey:", Foundation.nsString(key));

        cachedNodeObj = mnode;
      }
    }

    private void  _readPath(ID parent) {
      if (myPath.isEmpty())
        return;

      for (Node pn: myPath) {
        pn.readNode(parent);
        if (!pn.isValid())
          return;
        parent = pn.cachedNodeObj;
      }
    }
    private void  _resetPathCache() {
      for (Node pn: myPath)
        pn.cachedNodeObj = ID.NIL;
    }
  }

  public static String readStringVal(String domain, String key) {
    final Path result = new Path();
    result.myPath.add(new Path.Node("persistentDomainForName:", domain));
    return result.readStringVal(key);
  }

  public static boolean isDomainExists(String domain) {
    final Path result = new Path();
    result.myPath.add(new Path.Node("persistentDomainForName:", domain));
    return result.lastValidPos() >= 0;
  }

  public static void createPersistentDomain(@NotNull String domainName, @Nullable Map<String, Object> values) {
    final Foundation.NSAutoreleasePool pool = new Foundation.NSAutoreleasePool();
    try {
      final ID defaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults");
      if (defaults.equals(ID.NIL))
        return;
      final ID dict = Foundation.invoke("NSMutableDictionary", "new");
      if (values != null) {
        for (Map.Entry<String, Object> me: values.entrySet()) {
          final Object val = me.getValue();
          if (val instanceof String)
            Foundation.invoke(dict,"setObject:forKey:", Foundation.nsString((String)val), Foundation.nsString(me.getKey()));
          else if (val instanceof Map) {
            final ID internalDict = Foundation.invoke("NSMutableDictionary", "new");
            Foundation.invoke(dict,"setObject:forKey:", internalDict, Foundation.nsString(me.getKey()));
          } else
            LOG.error("unsupported type of domain value: " + val);
        }
      }
      Foundation.invoke(defaults, "setPersistentDomain:forName:", dict, Foundation.nsString(domainName));
    } finally {
      pool.drain();
    }
  }

  public static void removePersistentDomain(@NotNull String domainName) {
    final Foundation.NSAutoreleasePool pool = new Foundation.NSAutoreleasePool();
    try {
      final ID defaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults");
      if (defaults.equals(ID.NIL))
        return;
      Foundation.invoke(defaults, "removePersistentDomainForName:", Foundation.nsString(domainName));
    } finally {
      pool.drain();
    }
  }

  //
  // Touchbar related settings
  //

  // example of possible touchbar config : defaults read com.apple.touchbar.agent
  //{
  //  PresentationModeFnModes =     {
  //    functionKeys = app;
  //  };
  //  PresentationModeGlobal = functionKeys;
  //  PresentationModePerApp =     {
  //    "com.apple.calculator" = functionKeys;
  //  "com.eltima.cmd1.mas" = functionKeys;
  //  };
  //}
  private static final String ourTouchBarDomain = "com.apple.touchbar.agent";
  private static final String ourPerAppKey = "PresentationModePerApp";
  private static final String ourGlobalKey = "PresentationModeGlobal";
  private static final String ourShowFnValue = "functionKeys";

  public static boolean isShowFnKeysEnabled(String appId) {
    // 1. check global-mode setting
    final Path p = new Path();
    p.myPath.add(new Path.Node("persistentDomainForName:", ourTouchBarDomain));
    String sval = p.readStringVal(ourGlobalKey);
    if (sval != null && sval.equals(ourShowFnValue)) {
      // PresentationModeGlobal = functionKeys;
      return true;
    }

    // 2. check per-app setting
    p.myPath.add(new Path.Node("objectForKey:", ourPerAppKey));
    sval = p.readStringVal(appId);
    return sval != null && sval.equals(ourShowFnValue);
  }

  public static boolean isFnShowsAppControls() {
    //  PresentationModeFnModes =     {
    //    functionKeys = app;
    //  };
    final Path p = new Path();
    p.myPath.add(new Path.Node("persistentDomainForName:", ourTouchBarDomain));
    p.myPath.add(new Path.Node("objectForKey:", "PresentationModeFnModes"));

    final String sval = p.readStringVal("functionKeys");
    return sval != null && sval.equals("app");
  }

  /**
   * @return True when value has been changed
   */
  public static boolean setShowFnKeysEnabled(String appId, boolean val) { return setShowFnKeysEnabled(appId, val, false); }

  public static boolean setShowFnKeysEnabled(String appId, boolean val, boolean performExtraDebugChecks) {
    if (!isDomainExists(ourTouchBarDomain)) {
      final Map<String, Object> vals = new HashMap<>();
      vals.put(ourPerAppKey, new HashMap<>());
      createPersistentDomain(ourTouchBarDomain, vals);
      if (!isDomainExists(ourTouchBarDomain)) {
        LOG.error("can't create domain '" + ourTouchBarDomain + "'");
        return false;
      }
    }

    final Path path = new Path();
    path.myPath.add(new Path.Node("persistentDomainForName:", ourTouchBarDomain));
    path.myPath.add(new Path.Node("objectForKey:", ourPerAppKey));
    String sval = path.readStringVal(appId);
    final boolean settingEnabled = sval != null && sval.equals(ourShowFnValue);

    final String initDesc = "appId='" + appId
                            + "', value (requested be set) ='" + val
                            + "', initial path (tail) value = '" + sval
                            + "', path='" + path + "'";

    if (val == settingEnabled) {
      if (performExtraDebugChecks) LOG.error("nothing to change: " + initDesc);
      return false;
    }

    path.writeStringValue(appId, val ? ourShowFnValue : null);

    if (performExtraDebugChecks) {
      // just for embedded debug: make call of Foundation.invoke(myDefaults, "synchronize") - It waits for any pending asynchronous updates to the defaults database and returns; this method is unnecessary and shouldn't be used.
      sval = path.readStringVal(appId, true);
      final boolean isFNEnabled = sval != null && sval.equals(ourShowFnValue);
      if (val != isFNEnabled)
        LOG.error("can't write value '" + val + "' (was written just now, but read '" + sval + "'): " + initDesc);
      else
        LOG.error("value '" + val + "' was written from second attempt: " + initDesc);
    }

    return true;
  }

  private static final String TEST_APP_ID = "com.apple.terminal";

  // returns error description when fails (null when success)
  public static String testTouchBarSettingsWrite() {
    if (isDomainExists(ourTouchBarDomain)) {
      removePersistentDomain(ourTouchBarDomain);
      if (isDomainExists(ourTouchBarDomain))
        return "can't delete domain: " + ourTouchBarDomain;
    }

    final Map<String, Object> vals = new HashMap<>();
    vals.put("TestNSDefaultsKey", "TestNSDefaultsValue");
    vals.put("PresentationModePerApp", new HashMap<>());
    createPersistentDomain(ourTouchBarDomain, vals);

    if (!isDomainExists(ourTouchBarDomain))
      return "can't create domain: " + ourTouchBarDomain;

    final boolean enabled = isShowFnKeysEnabled(TEST_APP_ID);
    setShowFnKeysEnabled(TEST_APP_ID, !enabled);
    if (isShowFnKeysEnabled(TEST_APP_ID) == enabled)
      return "can't write " + ourTouchBarDomain + "." + ourPerAppKey + "=" + !enabled;

    setShowFnKeysEnabled(TEST_APP_ID, enabled);
    return isShowFnKeysEnabled(TEST_APP_ID) == enabled ? null : "can't write " + ourTouchBarDomain + "." + ourPerAppKey + "=" + enabled;
  }

  //
  // Appearance settings
  //

  public static boolean isDarkMenuBar() {
    assert SystemInfoRt.isMac;

    Foundation.NSAutoreleasePool pool = new Foundation.NSAutoreleasePool();
    try {
      ID defaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults");
      if (defaults.equals(ID.NIL)) {
        return false;
      }
      ID valObj = Foundation.invoke(defaults, "objectForKey:", Foundation.nsString("AppleInterfaceStyle"));
      if (valObj.equals(ID.NIL)) {
        return false;
      }

      String sval = Foundation.toStringViaUTF8(valObj);
      return sval != null && sval.equals("Dark");
    }
    finally {
      pool.drain();
    }
  }

  // for debug only
  private List<String> _listAllKeys() {
    List<String> res = new ArrayList<>(100);
    final Foundation.NSAutoreleasePool pool = new Foundation.NSAutoreleasePool();
    try {
      final ID defaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults");
      final ID allKeysDict = Foundation.invoke(defaults, "dictionaryRepresentation");
      final ID allKeysArr = Foundation.invoke(allKeysDict, "allKeys");
      final ID count = Foundation.invoke(allKeysArr, "count");
      for (int c = 0; c < count.intValue(); ++c) {
        final ID nsKeyName = Foundation.invoke(allKeysArr, "objectAtIndex:", c);
        final String keyName = Foundation.toStringViaUTF8(nsKeyName);
        //      System.out.println(keyName);
        res.add(keyName);
      }
      return res;
    } finally {
      pool.drain();
    }
  }
}
