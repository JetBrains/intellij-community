package com.intellij.cvsSupport2.connections.ssh;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.netbeans.lib.cvsclient.connection.PServerPasswordScrambler;

import java.util.Iterator;
import java.util.Map;

/**
 * author: lesya
 */
public class SSHPasswordProvider implements ApplicationComponent, JDOMExternalizable {

  private Map<String, String> myCvsRootToPasswordMap = new HashMap<String, String>();
  private Map<String, String> myCvsRootToStoringPasswordMap = new HashMap<String, String>();
  private static final String PASSWORDS = "passwords";
  private static final String PASSWORD = "password";
  private static final String CVSROOT_ATTR = "CVSROOT";
  private static final String PASSWORD_ATTR = "PASSWORD";

  public static SSHPasswordProvider getInstance() {
    return ApplicationManager.getApplication().getComponent(SSHPasswordProvider.class);
  }

  public String getComponentName() {
    return "SSHPasswordProvider";
  }

  public void initComponent() { }

  public void disposeComponent() {
    myCvsRootToPasswordMap.clear();
    myCvsRootToStoringPasswordMap.clear();
  }

  public String getPasswordForCvsRoot(String cvsRoot) {
    if (myCvsRootToStoringPasswordMap.containsKey(cvsRoot))
      return myCvsRootToStoringPasswordMap.get(cvsRoot);
    if (myCvsRootToPasswordMap.containsKey(cvsRoot))
      return myCvsRootToPasswordMap.get(cvsRoot);

    return null;
  }

  public void storePasswordForCvsRoot(String cvsRoot, String password, boolean storeInWorkspace) {
    if (storeInWorkspace) {
      myCvsRootToStoringPasswordMap.put(cvsRoot, password);
    }
    else {
      myCvsRootToPasswordMap.put(cvsRoot, password);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    Element passwords = new Element(PASSWORDS);
    for (Iterator<String> eachCvsRoot = myCvsRootToStoringPasswordMap.keySet().iterator(); eachCvsRoot.hasNext();) {
      Element password = new Element(PASSWORD);
      String cvsRoot = eachCvsRoot.next();
      password.setAttribute(CVSROOT_ATTR, cvsRoot);
      password.setAttribute(PASSWORD_ATTR, PServerPasswordScrambler.getInstance().scramble(myCvsRootToStoringPasswordMap.get(cvsRoot)));
      passwords.addContent(password);
    }
    element.addContent(passwords);
  }

  public void readExternal(Element element) throws InvalidDataException {
    Element passwords = element.getChild(PASSWORDS);
    for (Iterator eachPasswordElement = passwords.getChildren(PASSWORD).iterator(); eachPasswordElement.hasNext();){
      Element passElement = (Element)eachPasswordElement.next();
      String cvsRoot = passElement.getAttributeValue(CVSROOT_ATTR);
      String password = passElement.getAttributeValue(PASSWORD_ATTR);
      if ((cvsRoot != null) && (password != null))
        myCvsRootToStoringPasswordMap.put(cvsRoot, PServerPasswordScrambler.getInstance().unscramble(password));
    }

  }

  public void removePasswordFor(String stringRepsentation) {
    myCvsRootToPasswordMap.remove(stringRepsentation);
    myCvsRootToStoringPasswordMap.remove(stringRepsentation);
  }
}
