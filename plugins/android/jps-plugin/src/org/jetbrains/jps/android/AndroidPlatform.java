package org.jetbrains.jps.android;

import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;

public class AndroidPlatform {
  private final AndroidSdk mySdk;
  private final IAndroidTarget myTarget;
  private final int myPlatformToolsRevision;

  public AndroidPlatform(@NotNull AndroidSdk sdk, @NotNull IAndroidTarget target) {
    mySdk = sdk;
    myTarget = target;
    myPlatformToolsRevision = AndroidCommonUtils.parsePackageRevision(sdk.getSdkPath(), SdkConstants.FD_PLATFORM_TOOLS);
  }

  @NotNull
  public AndroidSdk getSdk() {
    return mySdk;
  }

  @NotNull
  public IAndroidTarget getTarget() {
    return myTarget;
  }

  public int getPlatformToolsRevision() {
    return myPlatformToolsRevision;
  }
}
