package com.intellij.cvsSupport2.config;

import com.intellij.cvsSupport2.connections.sshViaMaverick.SshTypesToUse;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

/**
 * author: lesya
 */
public class SshSettings implements JDOMExternalizable, Cloneable {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.connections.ssh.ui.SshSettings");

  public boolean USE_PPK = false;
  public String PATH_TO_PPK = "";
  public String PORT = "";

  public SshTypesToUse SSH_TYPE = SshTypesToUse.ALLOW_BOTH;


  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public SshSettings clone() {
    try {
      return (SshSettings)super.clone();
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
      return new SshSettings();
    }
  }

}
