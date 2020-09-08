// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.groovy.compiler.rt;

public final class GroovyCompilerMessage {

  private final String myCategory;
  private final String myMessage;
  private final String myUrl;
  private final int myLineNum;
  private final int myColumnNum;

  public GroovyCompilerMessage(String category, String message, String url, int lineNum, int columnNum) {
    myCategory = category;
    myMessage = message;
    myUrl = url;
    myLineNum = lineNum;
    myColumnNum = columnNum;
  }

  public String getCategory() {
    return myCategory;
  }

  public String getMessage() {
    return myMessage;
  }

  public String getUrl() {
    return myUrl;
  }

  public int getLineNum() {
    return myLineNum;
  }

  public int getColumnNum() {
    return myColumnNum;
  }
}
