package com.intellij.cvsSupport2.config;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

/**
 * author: lesya
 */
public class LocalSettings implements JDOMExternalizable, Cloneable {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.config.LocalSettings");

  @NonNls
  public String PATH_TO_CVS_CLIENT = "cvs";
  @NonNls
  public String SERVER_COMMAND = "server";

  private boolean myCvsClientVerified = false;

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public boolean isCvsClientVerified() {
    return myCvsClientVerified;
  }

  public void setCvsClientVerified(final boolean cvsClientVerified) {
    myCvsClientVerified = cvsClientVerified;
  }

  public LocalSettings clone() {
    try {
      return (LocalSettings)super.clone();
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
      return new LocalSettings();
    }
  }
}
