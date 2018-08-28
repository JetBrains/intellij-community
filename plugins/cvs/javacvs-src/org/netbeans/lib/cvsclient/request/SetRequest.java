package org.netbeans.lib.cvsclient.request;

public final class SetRequest extends AbstractRequest {
  private final String varName;
  private final String varValue;

  public SetRequest(String varName, String varValue) {
    this.varName = varName;
    this.varValue = varValue;
  }

  @Override
  public String getRequestString() {
    return "Set " + varName + "=" + varValue + "\n";
  }
}