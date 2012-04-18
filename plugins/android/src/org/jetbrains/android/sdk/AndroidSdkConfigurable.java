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
  private final SdkModel.Listener myListener;
  private final SdkModel mySdkModel;

  public AndroidSdkConfigurable(@NotNull SdkModel sdkModel, @NotNull SdkModificator sdkModificator) {
    mySdkModel = sdkModel;
    myForm = new AndroidSdkConfigurableForm(sdkModel, sdkModificator);
    myListener = new SdkModel.Listener() {
      public void sdkAdded(Sdk sdk) {
        if (sdk.getSdkType().equals(JavaSdk.getInstance())) {
          myForm.addJavaSdk(sdk);
        }
      }

      public void beforeSdkRemove(Sdk sdk) {
        if (sdk.getSdkType().equals(JavaSdk.getInstance())) {
          myForm.removeJavaSdk(sdk);
        }
      }

      public void sdkChanged(Sdk sdk, String previousName) {
        if (sdk.getSdkType().equals(JavaSdk.getInstance())) {
          myForm.updateJdks(sdk, previousName);
        }
      }

      public void sdkHomeSelected(final Sdk sdk, final String newSdkHome) {
        if (sdk.getSdkType().equals(AndroidSdkType.getInstance())) {
          myForm.internalJdkUpdate(sdk);
        }
      }
    };
    mySdkModel.addListener(myListener);
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
    final Sdk javaSdk = myForm.getSelectedSdk();
    AndroidSdkAdditionalData newData = new AndroidSdkAdditionalData(mySdk, javaSdk);
    newData.setBuildTarget(myForm.getSelectedBuildTarget());
    final SdkModificator modificator = mySdk.getSdkModificator();
    modificator.setVersionString(javaSdk != null ? javaSdk.getVersionString() : null);
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
    myForm.init(androidData.getJavaSdk(), mySdk, platform != null ? androidData.getBuildTarget(platform.getSdkData()) : null);
  }

  @Override
  public void disposeUIResources() {
    mySdkModel.removeListener(myListener);
  }
}
