// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.foundation;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class NSDefaults {
  private static final Logger LOG = Logger.getInstance(NSDefaults.class);

  // NOTE: skip call of Foundation.invoke(myDefaults, "synchronize") (when read settings)
  // It waits for any pending asynchronous updates to the defaults database and returns; this method is unnecessary and shouldn't be used.

  public static final String ourTouchBarDomain = "com.apple.touchbar.agent";
  public static final String ourTouchBarNode = "PresentationModePerApp";
  public static final String ourTouchBarShowFnValue = "functionKeys";

  private static class Path {
    private final @NotNull ArrayList<Node> myPath = new ArrayList<>();

    @Override
    public String toString() {
      String res = "";
      for (Node pn: myPath) {
        if (!res.isEmpty()) res += " | ";
        res += pn.toString();
      }
      return res;
    }

    String readStringVal(@NotNull String key) { return readStringVal(key, false); }

    String readStringVal(@NotNull String key, boolean doSyncronize) {
      if (myPath.isEmpty())
        return null;

      final Foundation.NSAutoreleasePool pool = new Foundation.NSAutoreleasePool();
      try {
        final ID defaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults");
        if (defaults == null || defaults.equals(ID.NIL))
          return null;

        if (doSyncronize) {
          // NOTE: AppleDoc proposes to skip call of Foundation.invoke(myDefaults, "synchronize") - "this method is unnecessary and shouldn't be used."
          Foundation.invoke(defaults, "synchronize");
        }

        _readPath(defaults);
        final Node tail = myPath.get(myPath.size() - 1);
        if (!tail.isValid())
          return null;

        final ID valObj = Foundation.invoke(tail.cachedNodeObj, "objectForKey:", Foundation.nsString(key));
        if (valObj == null || valObj.equals(ID.NIL))
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
        if (defaults == null || defaults.equals(ID.NIL))
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
        if (defaults == null || defaults.equals(ID.NIL))
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

    static @NotNull Path createDomainPath(@NotNull String domain, @NotNull String[] nodes) {
      final Path result = new Path();
      result.myPath.add(new Node("persistentDomainForName:", domain));
      for (String nodeName: nodes)
        result.myPath.add(new Node("objectForKey:", nodeName));
      return result;
    }

    static @NotNull Path createDomainPath(@NotNull String domain, @NotNull String nodeName) {
      final Path result = new Path();
      result.myPath.add(new Node("persistentDomainForName:", domain));
      result.myPath.add(new Node("objectForKey:", nodeName));
      return result;
    }

    private static class Node {
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
        if (nodeObj == null || nodeObj.equals(ID.NIL))
          return;

        cachedNodeObj = nodeObj;
      }

      private static ID _createDictionary() { return Foundation.invoke("NSMutableDictionary", "new"); }

      void writeStringValue(@NotNull String key, String val) {
        final ID mnode;
        if (!isValid()) {
          if (val == null) // nothing to erase
            return;

          mnode = _createDictionary();
        } else
          mnode = Foundation.invoke(cachedNodeObj, "mutableCopy");

        if (mnode == null || mnode.equals(ID.NIL))
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

  public static boolean isShowFnKeysEnabled(String appId) {
    final Path path = Path.createDomainPath(ourTouchBarDomain, ourTouchBarNode);
    final String sval = path.readStringVal(appId);
    return sval != null && sval.equals(ourTouchBarShowFnValue);
  }

  /**
   * @return True when value has been changed
   */
  public static boolean setShowFnKeysEnabled(String appId, boolean val) { return setShowFnKeysEnabled(appId, val, false); }

  public static boolean setShowFnKeysEnabled(String appId, boolean val, boolean performExtraDebugChecks) {
    final Path path = Path.createDomainPath(ourTouchBarDomain, ourTouchBarNode);
    String sval = path.readStringVal(appId);
    final boolean settingEnabled = sval != null && sval.equals(ourTouchBarShowFnValue);

    final String initDesc = "appId='" + appId
                            + "', value (requested be set) ='" + val
                            + "', initial path (tail) value = '" + sval
                            + "', path='" + path.toString() + "'";

    if (val == settingEnabled) {
      if (performExtraDebugChecks) LOG.error("nothing to change: " + initDesc);
      return false;
    }

    path.writeStringValue(appId, val ? ourTouchBarShowFnValue : null);

    if (performExtraDebugChecks) {
      // just for embedded debug: make call of Foundation.invoke(myDefaults, "synchronize") - It waits for any pending asynchronous updates to the defaults database and returns; this method is unnecessary and shouldn't be used.
      sval = path.readStringVal(appId, true);
      final boolean isFNEnabled = sval != null && sval.equals(ourTouchBarShowFnValue);
      if (val != isFNEnabled)
        LOG.error("can't write value '" + val + "' (was written just now, but read '" + sval + "'): " + initDesc);
      else
        LOG.error("value '" + val + "' was written from second attempt: " + initDesc);
    }

    return true;
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

  public static boolean isDarkMenuBar() {
    assert SystemInfo.isMac;

    final Foundation.NSAutoreleasePool pool = new Foundation.NSAutoreleasePool();
    try {
      final ID defaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults");
      if (defaults == null || defaults.equals(ID.NIL))
        return false;
      final ID valObj = Foundation.invoke(defaults, "objectForKey:", Foundation.nsString("AppleInterfaceStyle"));
      if (valObj == null || valObj.equals(ID.NIL))
        return false;

      final String sval = Foundation.toStringViaUTF8(valObj);
      return sval != null && sval.equals("Dark");
    } finally {
      pool.drain();
    }
  }

  // for debug
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
