/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.projectRoots;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;

/**
 * User: anna
 * Date: Nov 22, 2004
 */
public class Sandbox implements SdkAdditionalData, JDOMExternalizable{
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.devkit.projectRoots.Sandbox");

  @SuppressWarnings({"WeakerAccess"})
  public String mySandboxHome;

  private String mySdkName;
  private Sdk mySdk;

  private LocalFileSystem.WatchRequest mySandboxRoot = null;
  @NonNls private static final String SDK = "sdk";

  public Sandbox(String sandboxHome, Sdk sdk) {
    mySandboxHome = sandboxHome;
    if (mySandboxHome != null) {
      mySandboxRoot = LocalFileSystem.getInstance().addRootToWatch(mySandboxHome, true);
    }
    mySdk = sdk;
  }

  //readExternal()
  public Sandbox() {
  }

  public String getSandboxHome() {
    return mySandboxHome;
  }

  public Object clone() throws CloneNotSupportedException {
    return new Sandbox(mySandboxHome, getSdk());
  }

  public void checkValid(SdkModel sdkModel) throws ConfigurationException {
    if (mySandboxHome == null || mySandboxHome.length() == 0 || getSdk() == null){
      throw new ConfigurationException(DevKitBundle.message("sandbox.specification"));
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    LOG.assertTrue(mySandboxRoot == null);
    mySdkName = element.getAttributeValue(SDK);
    if (mySandboxHome != null) {
      mySandboxRoot = LocalFileSystem.getInstance().addRootToWatch(mySandboxHome, true);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    final Sdk sdk = getSdk();
    if (sdk != null) {
      element.setAttribute(SDK, sdk.getName());
    }
  }

  void cleanupWatchedRoots() {
    if (mySandboxRoot != null) {
      LocalFileSystem.getInstance().removeWatchedRoot(mySandboxRoot);
    }
  }

  @Nullable
  public Sdk getSdk() {
    final ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
    if (mySdk == null) {
      if (mySdkName != null) {
        mySdk = jdkTable.findJdk(mySdkName);
        mySdkName = null;
      }
      else {
        for (ProjectJdk jdk : jdkTable.getAllJdks()) {
          if (jdk.getSdkType() instanceof JavaSdk) {
            mySdk = jdk;
            break;
          }
        }
      }
    }
    return mySdk;
  }

  public void setSdk(final Sdk sdk) {
    mySdk = sdk;
  }
}
