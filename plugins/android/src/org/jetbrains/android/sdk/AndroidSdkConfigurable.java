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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSdkConfigurable implements AdditionalDataConfigurable {

  private final AndroidSdkConfigurableForm myForm;

  private Sdk mySdk;

  public AndroidSdkConfigurable(@NotNull SdkModel sdkModel, @NotNull SdkModificator sdkModificator) {
    myForm = new AndroidSdkConfigurableForm(sdkModel, sdkModificator);
  }

  @Override
  public void setSdk(Sdk sdk) {
    mySdk = sdk;
  }

  @Override
  public JComponent createComponent() {
    return myForm.getContentPanel();
  }

  @Override
  public boolean isModified() {
    final AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)mySdk.getSdkAdditionalData();
    Sdk javaSdk = data != null ? data.getJavaSdk() : null;
    return javaSdk != myForm.getSelectedSdk();
  }

  @Override
  public void apply() throws ConfigurationException {
    AndroidSdkAdditionalData newData = new AndroidSdkAdditionalData(mySdk, myForm.getSelectedSdk());
    newData.setBuildTarget(myForm.getSelectedBuildTarget());
    final SdkModificator modificator = mySdk.getSdkModificator();
    modificator.setSdkAdditionalData(newData);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        modificator.commitChanges();
      }
    });
  }

  @Override
  public void reset() {
    if (mySdk == null) {
      return;
    }
    SdkAdditionalData data = mySdk.getSdkAdditionalData();
    if (!(data instanceof AndroidSdkAdditionalData)) {
      return;
    }
    final AndroidSdkAdditionalData androidData = (AndroidSdkAdditionalData)data;
    AndroidPlatform platform = androidData.getAndroidPlatform();
    myForm.init(androidData.getJavaSdk(), mySdk, platform != null ? androidData.getBuildTarget(platform.getSdk()) : null);
  }

  @Override
  public void disposeUIResources() {
  }

  public void addJavaSdk(Sdk sdk) {
    myForm.addJavaSdk(sdk);
  }

  public void removeJavaSdk(Sdk sdk) {
    myForm.removeJavaSdk(sdk);
  }

  public void updateJavaSdkList(Sdk sdk, String previousName) {
    myForm.updateJdks(sdk, previousName);
  }

  public void internalJdkUpdate(Sdk sdk) {
    myForm.internalJdkUpdate(sdk);
  }
}
