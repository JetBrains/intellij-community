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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remote.DockerCredentialsHolder;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

public class DockerCredentialsHandler extends RemoteCredentialsHandlerBase<DockerCredentialsHolder> {

  public static final String DOCKER_PREFIX = "docker://";

  public DockerCredentialsHandler(DockerCredentialsHolder credentials) {
    super(credentials);
  }

  @Override
  public String getId() {
    // TODO [Docker] review
    DockerCredentialsHolder cred = getCredentials();
    String name = StringUtil.isNotEmpty(cred.getContainerName()) ? cred.getContainerName() : cred.getImageName();
    return DOCKER_PREFIX + name + "/";
  }

  @Override
  public void save(Element rootElement) {
    getCredentials().save(rootElement);
  }

  @Override
  public String getPresentableDetails(String interpreterPath) {
    DockerCredentialsHolder credentials = getCredentials();
    String containerName = StringUtil.isNotEmpty(credentials.getContainerName())
                           ? credentials.getContainerName() + " " : "";
    return "Docker " + containerName + "(" + credentials.getImageName() + ")";
  }

  @Override
  public void load(@Nullable Element rootElement) {
    if (rootElement != null) {
      getCredentials().load(rootElement);
    }
  }
}
