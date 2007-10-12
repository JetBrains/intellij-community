package org.jetbrains.groovy.compiler.rt;

public class CompilerMessage {
  private String myCategory;
  private String myMessage;
  private String myUrl;
  private int myLineNum;
  private int myColumnNum;

  public CompilerMessage(String category, String message, String url, int lineNum, int columnNum) {
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
