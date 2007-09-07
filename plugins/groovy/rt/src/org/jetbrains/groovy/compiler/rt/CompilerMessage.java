package org.jetbrains.groovy.compiler.rt;

public class CompilerMessage {
  String cathegory;
  String message;
  String url;
  int linenum;
  int colomnnum;

  public CompilerMessage(String cathegory, String message, String url, int linenum, int colomnnum) {
    this.cathegory = cathegory;
    this.message = message;
    this.url = url;
    this.linenum = linenum;
    this.colomnnum = colomnnum;
  }

  public String getCathegory() {
    return cathegory;
  }

  public String getMessage() {
    return message;
  }

  public String getUrl() {
    return url;
  }

  public int getLinenum() {
    return linenum;
  }

  public int getColomnnum() {
    return colomnnum;
  }
}
