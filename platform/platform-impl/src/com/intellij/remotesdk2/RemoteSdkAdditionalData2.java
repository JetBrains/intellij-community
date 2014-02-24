/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.remotesdk2;

import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.remotesdk.RemoteSdkCredentials;
import com.intellij.remotesdk.RemoteSdkCredentialsHolder;
import com.intellij.remotesdk.RemoteSdkProperties;

/**
 * @author traff
 */
public interface RemoteSdkAdditionalData2<T extends RemoteSdkCredentials>
  extends SdkAdditionalData, RemoteSdkProducer<T>, RemoteSdkProperties {
  void completeInitialization();

  boolean isInitialized();

  void setInitialized(boolean initialized);

  String getFullInterpreterPath();

  /**
   * This method switches to use of ssh-credentials based data
   * @param credentials credentials that specify connection
   */
  void setSshCredentials(RemoteSdkCredentialsHolder credentials);
}
