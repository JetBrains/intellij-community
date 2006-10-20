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

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jdom.Element;
import org.jetbrains.idea.devkit.DevKitBundle;

/**
 * User: anna
 * Date: Nov 22, 2004
 */
public class Sandbox implements SdkAdditionalData, JDOMExternalizable{
  @SuppressWarnings({"WeakerAccess"})
  public String mySandboxHome;

  private LocalFileSystem.WatchRequest mySandboxRoot = null;

  public Sandbox(String sandboxHome) {
    mySandboxHome = sandboxHome;
    mySandboxRoot = LocalFileSystem.getInstance().addRootToWatch(mySandboxHome, true);
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

  public void checkValid(SdkModel sdkModel) throws ConfigurationException {
    if (mySandboxHome == null || mySandboxHome.length() == 0){
      throw new ConfigurationException(DevKitBundle.message("sandbox.specification"));
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    mySandboxRoot = LocalFileSystem.getInstance().addRootToWatch(mySandboxHome, true);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  void cleanupWatchedRoots() {
    if (mySandboxRoot != null) {
      LocalFileSystem.getInstance().removeWatchedRoot(mySandboxRoot);
    }
  }
}
