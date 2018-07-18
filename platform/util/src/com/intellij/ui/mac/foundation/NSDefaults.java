// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  private static final String ourTouchBarDomain = "com.apple.touchbar.agent";
  private static final String ourTouchBarNode = "PresentationModePerApp";
  private static final String ourTouchBarShowFnValue = "functionKeys";

  private static final Domain EMPTY_DOMAIN = new Domain("nsdefaults_domain_stub", ID.NIL, ID.NIL);
  private static final Node EMPTY_NODE = new Node("nsdefaults_node_stub", EMPTY_DOMAIN, ID.NIL);

  @NotNull private final ID myDefaults;

  public NSDefaults() {
    final ID defaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults");
    myDefaults = defaults == null ? ID.NIL : defaults;
  }

  @Override
  public String toString() { return "Defaults=" + myDefaults; }

  @NotNull
  public Domain readDomain(@NotNull String domainName) {
    if (myDefaults.equals(ID.NIL))
      return EMPTY_DOMAIN;

    final ID domainObj = Foundation.invoke(myDefaults, "persistentDomainForName:", Foundation.nsString(domainName));
    if (domainObj == null || domainObj.equals(ID.NIL))
      return EMPTY_DOMAIN;

    return new Domain(domainName, domainObj, myDefaults);
  }

  public String readStringVal(String key) {
    if (myDefaults.equals(ID.NIL))
      return null;
    final ID valObj = Foundation.invoke(myDefaults, "stringForKey:", Foundation.nsString(key));
    return Foundation.toStringViaUTF8(valObj);
  }

  public static class Node {
    @NotNull private final String myNodeName;
    @NotNull private ID myNode;
    @NotNull private final Domain myDomain;

    Node(@NotNull String nodeName, @NotNull Domain domain, @NotNull ID node) {
      myNodeName = nodeName;
      myNode = node;
      myDomain = domain;
    }

    boolean isValid() { return !myNode.equals(ID.NIL); }

    @Override
    public String toString() { return "Node="+myNode+"; "+myDomain; }

    String readStringVal(@NotNull String key) {
      if (!isValid())
        return null;
      final ID valObj = Foundation.invoke(myNode, "objectForKey:", Foundation.nsString(key));
      return Foundation.toStringViaUTF8(valObj);
    }

    void setStringValue(@NotNull String key, String val) {
      if (!isValid())
        return;

      final ID mnode = Foundation.invoke(myNode, "mutableCopy");
      if (val != null)
        Foundation.invoke(mnode, "setObject:forKey:", Foundation.nsString(val), Foundation.nsString(key));
      else
        Foundation.invoke(mnode, "removeObjectForKey:", Foundation.nsString(key));

      myNode = mnode;
      myDomain.writeNode(this);
    }
  }

  public static class Domain {
    @NotNull private final String myDomainName;
    @NotNull private ID myDomain;
    @NotNull private final ID myDefaults;

    Domain(@NotNull String domainName, @NotNull ID domain, @NotNull ID defaults) {
      myDomainName = domainName;
      myDomain = domain;
      myDefaults = defaults;
    }

    public boolean isValid() { return !myDomain.equals(ID.NIL); }

    @Override
    public String toString() { return "Domain="+myDomain+"; "+myDefaults; }

    @NotNull
    public Node readNode(@NotNull String nodeName) {
      if (!isValid())
        return EMPTY_NODE;
      final ID nodeObj = Foundation.invoke(myDomain, "objectForKey:", Foundation.nsString(nodeName));
      if (nodeObj == null || nodeObj.equals(ID.NIL))
        return EMPTY_NODE;
      return new Node(nodeName, this, nodeObj);
    }

    public void writeNode(@NotNull Node node) {
      if (myDomain.equals(ID.NIL))
        return;

      final ID mdomain = Foundation.invoke(myDomain, "mutableCopy");
      Foundation.invoke(mdomain, "setObject:forKey:", node.myNode, Foundation.nsString(node.myNodeName));
      Foundation.invoke(myDefaults, "setPersistentDomain:forName:", mdomain, Foundation.nsString(myDomainName));
    }

    public String readStringVal(@NotNull String key) {
      if (!isValid())
        return null;
      final ID valObj = Foundation.invoke(myDomain, "objectForKey:", Foundation.nsString(key));
      return Foundation.toStringViaUTF8(valObj);
    }
  }

  public static boolean isShowFnKeysEnabled(String appId) {
    final String sval = new NSDefaults().readDomain(ourTouchBarDomain).readNode(ourTouchBarNode).readStringVal(appId);
    return sval != null && sval.equals(ourTouchBarShowFnValue);
  }

  /**
   * @return True when value has been changed
   */
  public static boolean setShowFnKeysEnabled(String appId, boolean val) {
    final Node appFnNode = new NSDefaults().readDomain(ourTouchBarDomain).readNode(ourTouchBarNode);
    if (!appFnNode.isValid())
      return false;

    final String sval = appFnNode.readStringVal(appId);
    final boolean settingEnabled = sval != null && sval.equals(ourTouchBarShowFnValue);
    if (val == settingEnabled)
      return false;

    appFnNode.setStringValue(appId, val ? ourTouchBarShowFnValue : null);
    return true;
  }

  @NotNull
  public static Domain readTouchBarDomain() { return new NSDefaults().readDomain(ourTouchBarDomain); }

  // for debug
  private List<String> _listAllKeys() {
    List<String> res = new ArrayList<String>(100);
    final ID allKeysDict = Foundation.invoke(myDefaults, "dictionaryRepresentation");
    final ID allKeysArr = Foundation.invoke(allKeysDict, "allKeys");
    final ID count = Foundation.invoke(allKeysArr, "count");
    for (int c = 0; c < count.intValue(); ++c) {
      final ID nsKeyName = Foundation.invoke(allKeysArr, "objectAtIndex:", c);
      final String keyName = Foundation.toStringViaUTF8(nsKeyName);
//      System.out.println(keyName);
      res.add(keyName);
    }
    return res;
  }
}
