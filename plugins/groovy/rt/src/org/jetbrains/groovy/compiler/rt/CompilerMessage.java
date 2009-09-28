package org.jetbrains.groovy.compiler.rt;

public class CompilerMessage {
  private final String myCategory;
  private final String myMessage;
  private final String myUrl;
  private final int myLineNum;
  private final int myColumnNum;
  public static final String ERROR = "error";
  public static final String WARNING = "warning";
  public static final String INFORMATION = "information";
  public static final String STATISTICS = "statistic";

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
