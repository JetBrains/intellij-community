package com.intellij.lang.ant.config;

import com.intellij.openapi.util.JDOMExternalizable;
import org.jdom.Element;

public abstract class ExecutionEvent implements JDOMExternalizable {
  public abstract String getTypeId();

  public abstract String getPresentableName();

  public void readExternal(Element element) {
  }

  public void writeExternal(Element element) {
  }
}
