package com.intellij.xdebugger.impl.ui;

import com.intellij.util.ArrayUtil;

/**
 * @author nik
 */
public class XDebugSessionData {
  private String[] myWatchExpressions;

  public XDebugSessionData(final String[] watchExpressions) {
    myWatchExpressions = watchExpressions;
  }

  public XDebugSessionData() {
    this(ArrayUtil.EMPTY_STRING_ARRAY);
  }

  public String[] getWatchExpressions() {
    return myWatchExpressions;
  }
}
