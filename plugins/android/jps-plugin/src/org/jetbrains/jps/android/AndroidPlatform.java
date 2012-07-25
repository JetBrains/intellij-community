package org.jetbrains.jps.android;

import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.model.JpsAndroidSdkProperties;
import org.jetbrains.jps.model.library.JpsTypedLibrary;

public class AndroidPlatform {
  private final JpsTypedLibrary<JpsAndroidSdkProperties> mySdk;
  private final IAndroidTarget myTarget;
  private final int myPlatformToolsRevision;
  private final int mySdkToolsRevision;

  public AndroidPlatform(@NotNull JpsTypedLibrary<JpsAndroidSdkProperties> sdk, @NotNull IAndroidTarget target) {
    mySdk = sdk;
    myTarget = target;
    final String homePath = sdk.getProperties().getHomePath();
    myPlatformToolsRevision = AndroidCommonUtils.parsePackageRevision(homePath, SdkConstants.FD_PLATFORM_TOOLS);
    mySdkToolsRevision = AndroidCommonUtils.parsePackageRevision(homePath, SdkConstants.FD_TOOLS);
  }

  @NotNull
  public JpsTypedLibrary<JpsAndroidSdkProperties> getSdk() {
    return mySdk;
  }

  @NotNull
  public IAndroidTarget getTarget() {
    return myTarget;
  }

  public int getPlatformToolsRevision() {
    return myPlatformToolsRevision;
  }

  public int getSdkToolsRevision() {
    return mySdkToolsRevision;
  }

  public boolean needToAddAnnotationsJarToClasspath() {
    return myTarget.getVersion().getApiLevel() <= 15;
  }
}
