package com.intellij.cvsSupport2.connections.ssh;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.netbeans.lib.cvsclient.connection.PServerPasswordScrambler;
import org.jetbrains.annotations.NonNls;

import java.util.Iterator;
import java.util.Map;

/**
 * author: lesya
 */
public class SSHPasswordProvider implements ApplicationComponent, JDOMExternalizable {

  private Map<String, String> myCvsRootToPasswordMap = new HashMap<String, String>();
  private Map<String, String> myCvsRootToStoringPasswordMap = new HashMap<String, String>();

  private Map<String, String> myCvsRootToPPKPasswordMap = new HashMap<String, String>();
  private Map<String, String> myCvsRootToStoringPPKPasswordMap = new HashMap<String, String>();

  @NonNls private static final String PASSWORDS = "passwords";
  @NonNls private static final String PASSWORD = "password";

  @NonNls private static final String PPKPASSWORDS = "ppkpasswords";

  @NonNls private static final String CVSROOT_ATTR = "CVSROOT";
  @NonNls private static final String PASSWORD_ATTR = "PASSWORD";

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
    myCvsRootToPPKPasswordMap.clear();
    myCvsRootToStoringPPKPasswordMap.clear();
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

  public String getPPKPasswordForCvsRoot(String cvsRoot) {
    if (myCvsRootToStoringPPKPasswordMap.containsKey(cvsRoot))
      return myCvsRootToStoringPPKPasswordMap.get(cvsRoot);
    if (myCvsRootToPPKPasswordMap.containsKey(cvsRoot))
      return myCvsRootToPPKPasswordMap.get(cvsRoot);

    return null;
  }

  public void storePPKPasswordForCvsRoot(String cvsRoot, String password, boolean storeInWorkspace) {
    if (storeInWorkspace) {
      myCvsRootToStoringPPKPasswordMap.put(cvsRoot, password);
    }
    else {
      myCvsRootToPPKPasswordMap.put(cvsRoot, password);
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

    passwords = new Element(PPKPASSWORDS);
    for (Iterator<String> eachCvsRoot = myCvsRootToStoringPPKPasswordMap.keySet().iterator(); eachCvsRoot.hasNext();) {
      Element password = new Element(PASSWORD);
      String cvsRoot = eachCvsRoot.next();
      password.setAttribute(CVSROOT_ATTR, cvsRoot);
      password.setAttribute(PASSWORD_ATTR, PServerPasswordScrambler.getInstance().scramble(myCvsRootToStoringPPKPasswordMap.get(cvsRoot)));
      passwords.addContent(password);
    }
    element.addContent(passwords);

  }

  public void readExternal(Element element) throws InvalidDataException {
    Element passwords = element.getChild(PASSWORDS);
    if (passwords != null) {
      for (Iterator eachPasswordElement = passwords.getChildren(PASSWORD).iterator(); eachPasswordElement.hasNext();){
        Element passElement = (Element)eachPasswordElement.next();
        String cvsRoot = passElement.getAttributeValue(CVSROOT_ATTR);
        String password = passElement.getAttributeValue(PASSWORD_ATTR);
        if ((cvsRoot != null) && (password != null))
          myCvsRootToStoringPasswordMap.put(cvsRoot, PServerPasswordScrambler.getInstance().unscramble(password));
      }
    }
    passwords = element.getChild(PPKPASSWORDS);
    if (passwords != null) {
      for (Iterator eachPasswordElement = passwords.getChildren(PASSWORD).iterator(); eachPasswordElement.hasNext();){
        Element passElement = (Element)eachPasswordElement.next();
        String cvsRoot = passElement.getAttributeValue(CVSROOT_ATTR);
        String password = passElement.getAttributeValue(PASSWORD_ATTR);
        if ((cvsRoot != null) && (password != null))
          myCvsRootToStoringPPKPasswordMap.put(cvsRoot, PServerPasswordScrambler.getInstance().unscramble(password));
      }
    }

  }

  public void removePasswordFor(String stringRepsentation) {
    myCvsRootToPasswordMap.remove(stringRepsentation);
    myCvsRootToStoringPasswordMap.remove(stringRepsentation);
  }

  public void removePPKPasswordFor(String stringRepsentation) {
    myCvsRootToPPKPasswordMap.remove(stringRepsentation);
    myCvsRootToStoringPPKPasswordMap.remove(stringRepsentation);
  }

}
