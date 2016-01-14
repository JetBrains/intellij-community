/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.remote;

import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Koshevoy
 */
public class DockerCredentialsHolder {
  public static final String DOCKER_ACCOUNT_NAME = "DOCKER_ACCOUNT_NAME";
  public static final String DOCKER_IMAGE_NAME = "DOCKER_IMAGE_NAME";
  public static final String DOCKER_CONTAINER_NAME = "DOCKER_CONTAINER_NAME";
  public static final String DOCKER_REMOTE_PROJECT_PATH = "DOCKER_REMOTE_PROJECT_PATH";

  private String myAccountName;

  private String myImageName;

  private String myContainerName;

  private String myRemoteProjectPath;

  public DockerCredentialsHolder() {
  }

  public DockerCredentialsHolder(String accountName,
                                 String imageName,
                                 String containerName,
                                 String remoteProjectPath) {
    myAccountName = accountName;
    myImageName = imageName;
    myContainerName = containerName;
    myRemoteProjectPath = remoteProjectPath;
  }

  public String getAccountName() {
    return myAccountName;
  }

  public String getImageName() {
    return myImageName;
  }

  public String getContainerName() {
    return myContainerName;
  }

  public String getRemoteProjectPath() {
    return myRemoteProjectPath;
  }

  public void setAccountName(String accountName) {
    myAccountName = accountName;
  }

  public void setImageName(String imageName) {
    myImageName = imageName;
  }

  public void setContainerName(String containerName) {
    myContainerName = containerName;
  }

  public void setRemoteProjectPath(String remoteProjectPath) {
    myRemoteProjectPath = remoteProjectPath;
  }

  public void save(@NotNull Element element) {
    if (StringUtil.isNotEmpty(myAccountName)) {
      element.setAttribute(DOCKER_ACCOUNT_NAME, myAccountName);
    }
    element.setAttribute(DOCKER_IMAGE_NAME, myImageName);
    if (StringUtil.isNotEmpty(myContainerName)) {
      element.setAttribute(DOCKER_CONTAINER_NAME, myContainerName);
    }
    element.setAttribute(DOCKER_REMOTE_PROJECT_PATH, myRemoteProjectPath);
  }

  public void load(@NotNull Element element) {
    myAccountName = element.getAttributeValue(DOCKER_ACCOUNT_NAME);
    myImageName = element.getAttributeValue(DOCKER_IMAGE_NAME);
    myContainerName = element.getAttributeValue(DOCKER_CONTAINER_NAME);
    myRemoteProjectPath = element.getAttributeValue(DOCKER_REMOTE_PROJECT_PATH);
  }
}
