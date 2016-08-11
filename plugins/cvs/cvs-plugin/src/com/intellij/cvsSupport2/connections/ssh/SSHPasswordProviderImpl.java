/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.cvsSupport2.connections.ssh;

import com.intellij.openapi.components.NamedComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.connection.PServerPasswordScrambler;

import java.util.List;
import java.util.Map;

/**
 * author: lesya
 */
public class SSHPasswordProviderImpl implements NamedComponent, JDOMExternalizable, SSHPasswordProvider {

  private final Map<String, String> myCvsRootToPasswordMap = new HashMap<>();
  private final Map<String, String> myCvsRootToStoringPasswordMap = new HashMap<>();

  private final Map<String, String> myCvsRootToPPKPasswordMap = new HashMap<>();
  private final Map<String, String> myCvsRootToStoringPPKPasswordMap = new HashMap<>();

  private final Object myLock = new Object();

  @NonNls private static final String PASSWORDS = "passwords";
  @NonNls private static final String PASSWORD = "password";

  @NonNls private static final String PPKPASSWORDS = "ppkpasswords";

  @NonNls private static final String CVSROOT_ATTR = "CVSROOT";
  @NonNls private static final String PASSWORD_ATTR = "PASSWORD";

  public static SSHPasswordProviderImpl getInstance() {
    return ServiceManager.getService(SSHPasswordProviderImpl.class);
  }

  @NotNull
  public String getComponentName() {
    return "SSHPasswordProvider";
  }

  @Nullable
  public String getPasswordForCvsRoot(String cvsRoot) {
    synchronized (myLock) {
      String password = myCvsRootToStoringPasswordMap.get(cvsRoot);
      if (password != null) {
        return password;
      }
      return myCvsRootToPasswordMap.get(cvsRoot);
    }
  }

  public void storePasswordForCvsRoot(String cvsRoot, String password, boolean storeInWorkspace) {
    synchronized (myLock) {
      if (storeInWorkspace) {
        myCvsRootToStoringPasswordMap.put(cvsRoot, password);
      }
      else {
        myCvsRootToPasswordMap.put(cvsRoot, password);
      }
    }
  }

  @Nullable
  public String getPPKPasswordForCvsRoot(String cvsRoot) {
    synchronized (myLock) {
      String password = myCvsRootToStoringPPKPasswordMap.get(cvsRoot);
      if (password != null) {
        return password;
      }
      return myCvsRootToPPKPasswordMap.get(cvsRoot);
    }
  }

  public void storePPKPasswordForCvsRoot(String cvsRoot, String password, boolean storeInWorkspace) {
    synchronized (myLock) {
      if (storeInWorkspace) {
        myCvsRootToStoringPPKPasswordMap.put(cvsRoot, password);
      }
      else {
        myCvsRootToPPKPasswordMap.put(cvsRoot, password);
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    Element passwords = new Element(PASSWORDS);
    for (final String cvsRoot : myCvsRootToStoringPasswordMap.keySet()) {
      Element password = new Element(PASSWORD);
      password.setAttribute(CVSROOT_ATTR, cvsRoot);
      password.setAttribute(PASSWORD_ATTR, PServerPasswordScrambler.getInstance().scramble(myCvsRootToStoringPasswordMap.get(cvsRoot)));
      passwords.addContent(password);
    }
    element.addContent(passwords);

    Element ppkPasswords = new Element(PPKPASSWORDS);
    for (final String cvsRoot : myCvsRootToStoringPPKPasswordMap.keySet()) {
      Element password = new Element(PASSWORD);
      password.setAttribute(CVSROOT_ATTR, cvsRoot);
      password.setAttribute(PASSWORD_ATTR, PServerPasswordScrambler.getInstance().scramble(myCvsRootToStoringPPKPasswordMap.get(cvsRoot)));
      ppkPasswords.addContent(password);
    }
    element.addContent(ppkPasswords);
  }

  public void readExternal(Element element) throws InvalidDataException {
    Element passwords = element.getChild(PASSWORDS);
    if (passwords != null) {
      for (Element passElement : (List<Element>)passwords.getChildren(PASSWORD)) {
        String cvsRoot = passElement.getAttributeValue(CVSROOT_ATTR);
        String password = passElement.getAttributeValue(PASSWORD_ATTR);
        if ((cvsRoot != null) && (password != null)) {
          myCvsRootToStoringPasswordMap.put(cvsRoot, PServerPasswordScrambler.getInstance().unscramble(password));
        }
      }
    }

    Element ppkPasswords = element.getChild(PPKPASSWORDS);
    if (ppkPasswords != null) {
      for (Element passElement : (List<Element>)ppkPasswords.getChildren(PASSWORD)) {
        String cvsRoot = passElement.getAttributeValue(CVSROOT_ATTR);
        String password = passElement.getAttributeValue(PASSWORD_ATTR);
        if ((cvsRoot != null) && (password != null)) {
          myCvsRootToStoringPPKPasswordMap.put(cvsRoot, PServerPasswordScrambler.getInstance().unscramble(password));
        }
      }
    }

  }

  public void removePasswordFor(String stringRepresentation) {
    synchronized (myLock) {
      myCvsRootToPasswordMap.remove(stringRepresentation);
      myCvsRootToStoringPasswordMap.remove(stringRepresentation);
    }
  }

  public void removePPKPasswordFor(String stringRepresentation) {
    synchronized (myLock) {
      myCvsRootToPPKPasswordMap.remove(stringRepresentation);
      myCvsRootToStoringPPKPasswordMap.remove(stringRepresentation);
    }
  }
}
