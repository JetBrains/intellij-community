package com.intellij.remotesdk;

import com.intellij.openapi.projectRoots.SdkAdditionalData;

/**
 * @author traff
 */
public interface RemoteSdkAdditionalData extends RemoteSdkData, SdkAdditionalData {
  void completeInitialization();
}
