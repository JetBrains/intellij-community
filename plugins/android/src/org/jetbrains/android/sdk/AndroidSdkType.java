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

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.intellij.CommonBundle;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSdkType extends SdkType implements JavaSdkType {

  @NonNls public static final String SDK_NAME = "Android SDK";

  public AndroidSdkType() {
    super(SDK_NAME);
  }

  @Nullable
  @Override
  public String getBinPath(Sdk sdk) {
    final Sdk internalJavaSdk = getInternalJavaSdk(sdk);
    return internalJavaSdk == null ? null : JavaSdk.getInstance().getBinPath(internalJavaSdk);
  }

  @Nullable
  public String getToolsPath(Sdk sdk) {
    final Sdk jdk = getInternalJavaSdk(sdk);
    if (jdk != null && jdk.getVersionString() != null) {
      return JavaSdk.getInstance().getToolsPath(jdk);
    }
    return null;
  }

  @Nullable
  public String getVMExecutablePath(Sdk sdk) {
    final Sdk internalJavaSdk = getInternalJavaSdk(sdk);
    return internalJavaSdk == null ? null : JavaSdk.getInstance().getVMExecutablePath(internalJavaSdk);
  }

  @Override
  public String suggestHomePath() {
    return null;
  }

  @Override
  public boolean isValidSdkHome(String path) {
    if (AndroidSdkUtils.isAndroidSdk(path)) {
      return true;
    }

    File f = new File(path).getParentFile();
    if (f != null) {
      f = f.getParentFile();
      if (f != null) {
        return AndroidSdkUtils.isAndroidSdk(f.getPath());
      }
    }
    return false;
  }

  @Override
  public String getVersionString(Sdk sdk) {
    final Sdk internalJavaSdk = getInternalJavaSdk(sdk);
    return internalJavaSdk != null ? internalJavaSdk.getVersionString() : null;
  }

  @Nullable
  @Override
  public String suggestSdkName(String currentSdkName, String sdkHome) {
    return SDK_NAME;
  }

  @Override
  public boolean setupSdkPaths(Sdk sdk, SdkModel sdkModel) {
    MessageBuildingSdkLog log = new MessageBuildingSdkLog();
    String homePath = sdk.getHomePath();
    assert homePath != null;

    AndroidSdk sdkObject = null;

    final boolean sdkFolderChosen = AndroidSdkUtils.isAndroidSdk(homePath);

    if (sdkFolderChosen) {
      sdkObject = AndroidSdk.parse(homePath, log);
    }
    else {
      File f = new File(homePath).getParentFile();
      if (f != null) {
        f = f.getParentFile();
        if (f != null) {
          final String sdkRootPath = f.getPath();
          sdkObject = AndroidSdk.parse(sdkRootPath, log);
          if (sdkObject != null) {
            final SdkModificator modificator = sdk.getSdkModificator();
            modificator.setHomePath(sdkObject.getLocation());
            modificator.commitChanges();
          }
        }
      }
    }

    if (sdkObject == null) {
      String errorMessage = log.getErrorMessage().length() > 0 ? log.getErrorMessage() : AndroidBundle.message("cannot.parse.sdk.error");
      Messages.showErrorDialog(errorMessage, "SDK parsing error");
      return false;
    }

    IAndroidTarget selectedTarget;

    if (!sdkFolderChosen) {
      // then platform folder chosen
      selectedTarget = sdkObject.findTargetByLocation(FileUtil.toSystemIndependentName(homePath));
      if (selectedTarget == null) {
        Messages.showErrorDialog("The selected directory is not a valid Android platform or add-on", CommonBundle.getErrorTitle());
        return false;
      }
    }
    else {
      IAndroidTarget[] targets = sdkObject.getTargets();

      if (targets.length == 0) {
        Messages.showErrorDialog(AndroidBundle.message("no.android.targets.error"), CommonBundle.getErrorTitle());
        return false;
      }

      String[] targetNames = new String[targets.length];

      String newestPlatform = null;
      AndroidVersion version = null;

      for (int i = 0; i < targets.length; i++) {
        IAndroidTarget target = targets[i];
        String targetName = AndroidSdkUtils.getTargetPresentableName(target);
        targetNames[i] = targetName;
        if (target.isPlatform() && (version == null || target.getVersion().compareTo(version) > 0)) {
          newestPlatform = targetName;
          version = target.getVersion();
        }
      }

      int choice =
        Messages.showChooseDialog("Select build target", "Create new Android SDK", targetNames,
                                  newestPlatform != null ? newestPlatform : targetNames[0], Messages.getQuestionIcon());

      if (choice == -1) {
        return false;
      }
      selectedTarget = targets[choice];
    }

    final List<String> javaSdks = new ArrayList<String>();
    final Sdk[] sdks = sdkModel.getSdks();
    for (Sdk jdk : sdks) {
      if (AndroidSdkUtils.isApplicableJdk(jdk)) {
        javaSdks.add(jdk.getName());
      }
    }

    if (javaSdks.isEmpty()){
      Messages.showErrorDialog(AndroidBundle.message("no.jdk.for.android.found.error"), "No Java SDK found");
      return false;
    }

    int choice = Messages
      .showChooseDialog("Please select Java SDK", "Select internal Java platform", ArrayUtil.toStringArray(javaSdks), javaSdks.get(0),
                        Messages.getQuestionIcon());

    if (choice == -1) {
      return false;
    }

    final String name = javaSdks.get(choice);
    final Sdk jdk = sdkModel.findSdk(name);

    AndroidSdkUtils.setUpSdk(sdk, jdk, sdks, selectedTarget, true);

    return true;
  }

  @Override
  public AdditionalDataConfigurable createAdditionalDataConfigurable(SdkModel sdkModel, SdkModificator sdkModificator) {
    final AndroidSdkConfigurable c = new AndroidSdkConfigurable(sdkModel, sdkModificator);

    sdkModel.addListener(new SdkModel.Listener() {
      public void sdkAdded(Sdk sdk) {
        if (sdk.getSdkType().equals(JavaSdk.getInstance())) {
          c.addJavaSdk(sdk);
        }
      }

      public void beforeSdkRemove(Sdk sdk) {
        if (sdk.getSdkType().equals(JavaSdk.getInstance())) {
          c.removeJavaSdk(sdk);
        }
      }

      public void sdkChanged(Sdk sdk, String previousName) {
        if (sdk.getSdkType().equals(JavaSdk.getInstance())) {
          c.updateJavaSdkList(sdk, previousName);
        }
      }

      public void sdkHomeSelected(final Sdk sdk, final String newSdkHome) {
        if (sdk.getSdkType().equals(AndroidSdkType.getInstance())) {
          c.internalJdkUpdate(sdk);
        }
      }
    });

    return c;
  }

  public void saveAdditionalData(SdkAdditionalData data, Element e) {
    if (data instanceof AndroidSdkAdditionalData) {
      ((AndroidSdkAdditionalData)data).save(e);
    }
  }

  @Override
  public SdkAdditionalData loadAdditionalData(Sdk currentSdk, Element additional) {
    return new AndroidSdkAdditionalData(currentSdk, additional);
  }

  @Override
  public String getPresentableName() {
    return AndroidBundle.message("android.sdk.presentable.name");
  }

  @Override
  public Icon getIcon() {
    return AndroidUtils.ANDROID_ICON;
  }

  @Override
  public Icon getIconForAddAction() {
    return getIcon();
  }

  @Override
  public boolean supportHomePathChanging() {
    return false;
  }

  @Nullable
  private static Sdk getInternalJavaSdk(Sdk sdk) {
    final SdkAdditionalData data = sdk.getSdkAdditionalData();
    if (data instanceof AndroidSdkAdditionalData) {
      return ((AndroidSdkAdditionalData)data).getJavaSdk();
    }
    return null;
  }

  public static AndroidSdkType getInstance() {
    return SdkType.findInstance(AndroidSdkType.class);
  }
}
