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
package org.jetbrains.android.sdk;

import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.*;
import org.jdom.Element;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSdkAdditionalData implements SdkAdditionalData {

  @NonNls private static final String JDK = "jdk";
  @NonNls private static final String BUILD_TARGET = "sdk";

  private String myJavaSdkName;
  private final Sdk myAndroidSdk;
  private Sdk myJavaSdk;

  // hash string
  private String myBuildTarget;

  private AndroidPlatform myAndroidPlatform = null;

  public AndroidSdkAdditionalData(@NotNull Sdk androidSdk, Sdk javaSdk) {
    myJavaSdk = javaSdk;
    myAndroidSdk = androidSdk;
  }

  public AndroidSdkAdditionalData(@NotNull Sdk androidSdk, @NotNull Element element) {
    myAndroidSdk = androidSdk;
    myJavaSdkName = element.getAttributeValue(JDK);
    myBuildTarget = element.getAttributeValue(BUILD_TARGET);
  }

  public AndroidSdkAdditionalData(Sdk androidSdk) {
    myAndroidSdk = androidSdk;
  }

  @Override
  public void checkValid(SdkModel sdkModel) throws ConfigurationException {
    if (getJavaSdk() == null) {
      throw new ConfigurationException(AndroidBundle.message("android.sdk.configure.jdk.error"));
    }
  }

  public Object clone() throws CloneNotSupportedException {
    AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)super.clone();
    data.setJavaSdk(getJavaSdk());
    data.myBuildTarget = myBuildTarget;
    return data;
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
          if (AndroidSdkUtils.isApplicableJdk(jdk)) {
            myJavaSdk = jdk;
            break;
          }
        }
      }
    }
    return myJavaSdk;
  }

  public void setJavaSdk(final Sdk javaSdk) {
    myJavaSdk = javaSdk;
  }

  public void setBuildTarget(IAndroidTarget target) {
    myBuildTarget = target != null ? target.hashString() : null;
  }

  public void save(Element element) {
    final Sdk sdk = getJavaSdk();
    if (sdk != null) {
      element.setAttribute(JDK, sdk.getName());
    }
    if (myBuildTarget != null) {
      element.setAttribute(BUILD_TARGET, myBuildTarget);
    }
  }

  @Nullable
  public IAndroidTarget getBuildTarget(@NotNull AndroidSdk sdkObject) {
    return myBuildTarget != null ? sdkObject.findTargetByHashString(myBuildTarget) : null;
  }

  @Nullable
  public AndroidPlatform getAndroidPlatform() {
    if (myAndroidPlatform == null) {
      myAndroidPlatform = AndroidPlatform.parse(myAndroidSdk);
    }
    return myAndroidPlatform;
  }
}
