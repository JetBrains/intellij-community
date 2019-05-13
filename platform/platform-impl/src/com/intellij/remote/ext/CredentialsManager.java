/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.remote.ext;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.remote.CredentialsType;
import com.intellij.remote.RemoteSdkAdditionalData;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class CredentialsManager {

  public static CredentialsManager getInstance() {
    return ServiceManager.getService(CredentialsManager.class);
  }

  public abstract List<CredentialsType> getAllTypes();

  public abstract List<CredentialsTypeEx> getExTypes();

  public abstract void loadCredentials(String interpreterPath, @Nullable Element element, RemoteSdkAdditionalData data);
}
