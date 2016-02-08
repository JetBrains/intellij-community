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

import com.intellij.remote.CredentialsType;
import com.intellij.remote.RemoteCredentialsHolder;
import com.intellij.remote.VagrantBasedCredentialsHolder;
import com.intellij.remote.WebDeploymentCredentialsHolder;

public interface CredentialsCase<T> {

  CredentialsType<T> getType();

  void process(T credentials);


  abstract class Ssh implements CredentialsCase<RemoteCredentialsHolder> {

    @Override
    public CredentialsType<RemoteCredentialsHolder> getType() {
      return CredentialsType.SSH_HOST;
    }
  }

  abstract class Vagrant implements CredentialsCase<VagrantBasedCredentialsHolder> {

    @Override
    public CredentialsType<VagrantBasedCredentialsHolder> getType() {
      return CredentialsType.VAGRANT;
    }
  }

  abstract class WebDeployment implements CredentialsCase<WebDeploymentCredentialsHolder> {

    @Override
    public CredentialsType<WebDeploymentCredentialsHolder> getType() {
      return CredentialsType.WEB_DEPLOYMENT;
    }
  }
}
