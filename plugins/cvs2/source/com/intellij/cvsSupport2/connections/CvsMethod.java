package com.intellij.cvsSupport2.connections;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;

/**
 * author: lesya
 */
public class CvsMethod {

  public static final CvsMethod EXT_METHOD = new CvsMethod("ext", true, true, true, false);
  public static final CvsMethod PSERVER_METHOD = new CvsMethod("pserver", true, true, true, true);
  public static final CvsMethod SSH_METHOD = new CvsMethod("ssh", "ssh (internal implementation)", true, true, true, true);

  public static final CvsMethod LOCAL_METHOD = new CvsMethod(null, "local", false, false, false, false);

  private final String myName;
  private final String myDescription;
  public static CvsMethod[] AVAILABLE_METHODS =
    new CvsMethod[]{PSERVER_METHOD,
                    EXT_METHOD,
                    SSH_METHOD,
                    LOCAL_METHOD};

  private boolean myHasUserValue;
  private boolean myHasHostValue;
  private boolean myHasPortValue;
  private boolean mySupportsProxyConnection;


  public CvsMethod(String name,
                   String description,
                   boolean hasUserValue,
                   boolean hasHostValue,
                   boolean hasPortValue,
                   boolean supportsProxy) {
    myName = name;
    myDescription = description;
    myHasUserValue = hasUserValue;
    myHasHostValue = hasHostValue;
    myHasPortValue = hasPortValue;
    mySupportsProxyConnection = supportsProxy;
  }

  public CvsMethod(String name,
                   boolean hasUserValue,
                   boolean hasHostValue,
                   boolean hasPortValue,
                   boolean supportsProxy) {
    this(name, name, hasUserValue, hasHostValue, hasPortValue, supportsProxy);
  }


  public String toString() {
    return myDescription;
  }

  public String getName() {
    return myName;
  }

  public String getDisplayName() {
    return myDescription;
  }

  public static CvsMethod getValue(String method) {
    CvsMethod[] availableMethods = AVAILABLE_METHODS;
    for (int i = 0; i < availableMethods.length; i++) {
      CvsMethod availableMethod = availableMethods[i];
      if (Comparing.equal(availableMethod.getName(), method)) return availableMethod;
    }
    return null;
  }

  public boolean hasUserValue() {
    return myHasUserValue;
  }

  public boolean hasHostValue() {
    return myHasHostValue;
  }

  public boolean hasPortValue() {
    return myHasPortValue;
  }

  public boolean supportsProxyConnection() {
    return mySupportsProxyConnection;
  }
}
