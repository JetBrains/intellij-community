/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.projectRoots.ValidatableSdkAdditionalData;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;

public class Sandbox implements ValidatableSdkAdditionalData {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.devkit.projectRoots.Sandbox");

  @SuppressWarnings("WeakerAccess")
  public String mySandboxHome;
  private final Sdk myCurrentJdk;

  private String myJavaSdkName;
  private Sdk myJavaSdk;

  private LocalFileSystem.WatchRequest mySandboxRoot;
  @NonNls private static final String SDK = "sdk";

  public Sandbox(String sandboxHome, Sdk javaSdk, Sdk currentJdk) {
    mySandboxHome = sandboxHome;
    myCurrentJdk = currentJdk;
    if (mySandboxHome != null) {
      mySandboxRoot = LocalFileSystem.getInstance().addRootToWatch(mySandboxHome, true);
    }
    myJavaSdk = javaSdk;
  }

  //readExternal()
  public Sandbox(Sdk currentSdk) {
    myCurrentJdk = currentSdk;
  }

  public String getSandboxHome() {
    return mySandboxHome;
  }

  @Override
  public void checkValid(SdkModel sdkModel) throws ConfigurationException {
    if (StringUtil.isEmpty(mySandboxHome)) {
      throw new ConfigurationException(DevKitBundle.message("sandbox.specification"));
    }
    if (getJavaSdk() == null) {
      throw new ConfigurationException(DevKitBundle.message("jdk.specification"));
    }
  }

  void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    LOG.assertTrue(mySandboxRoot == null);
    myJavaSdkName = element.getAttributeValue(SDK);
    if (mySandboxHome != null) {
      mySandboxRoot = LocalFileSystem.getInstance().addRootToWatch(mySandboxHome, true);
    }
  }

  void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    final Sdk sdk = getJavaSdk();
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
  public Sdk getJavaSdk() {
    final ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
    if (myJavaSdk == null) {
      if (myJavaSdkName != null) {
        myJavaSdk = jdkTable.findJdk(myJavaSdkName);
        myJavaSdkName = null;
      }
      else {
        for (Sdk jdk : jdkTable.getAllJdks()) {
          if (IdeaJdk.isValidInternalJdk(myCurrentJdk, jdk)) {
            myJavaSdk = jdk;
            break;
          }
        }
      }
    }
    return myJavaSdk;
  }

  void setJavaSdk(final Sdk javaSdk) {
    myJavaSdk = javaSdk;
  }
}
