package org.jetbrains.idea.devkit.projectRoots;

import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import org.jdom.Element;

/**
 * User: anna
 * Date: Nov 22, 2004
 */
public class Sandbox implements SdkAdditionalData, JDOMExternalizable{
  public String mySandboxHome;

  public Sandbox(String sandboxHome) {
    mySandboxHome = sandboxHome;
  }

  //readExternal()
  public Sandbox() {
  }

  public String getSandboxHome() {
    return mySandboxHome;
  }

  public Object clone() throws CloneNotSupportedException {
    return new Sandbox(mySandboxHome);
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}
