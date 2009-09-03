package com.intellij.cvsSupport2.config;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

/**
 * author: lesya
 */

public class AbstractConfiguration implements JDOMExternalizable, ProjectComponent {
  private final String myName;

  public AbstractConfiguration(@NonNls String name) {
    myName = name;
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public String getComponentName() {
    return myName;
  }

  public void initComponent() { }

  public void disposeComponent() {
  }
}
