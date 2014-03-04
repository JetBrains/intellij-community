package com.intellij.remotesdk;

import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.remote.RemoteSdkCredentials;

/**
 * @deprecated Remove in IDEA 14
 * @author traff
 */
public interface RemoteSdkAdditionalData extends RemoteSdkCredentials, SdkAdditionalData {
  void completeInitialization();
}
