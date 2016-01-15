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
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexander Koshevoy
 */
public class DockerCredentialsHolder {
  public static final String DOCKER_MACHINE_NAME = "DOCKER_MACHINE_NAME";
  public static final String DOCKER_IMAGE_NAME = "DOCKER_IMAGE_NAME";
  public static final String DOCKER_CONTAINER_NAME = "DOCKER_CONTAINER_NAME";
  public static final String DOCKER_REMOTE_PROJECT_PATH = "DOCKER_REMOTE_PROJECT_PATH";

  public static final String DOCKER_COMPOSE_FILE_PATH = "DOCKER_COMPOSE_FILE_PATH";
  public static final String DOCKER_COMPOSE_SERVICE_NAME = "DOCKER_COMPOSE_SERVICE_NAME";

  private String myMachineName;

  private String myImageName;

  private String myContainerName;

  private String myComposeFilePath;

  private String myComposeServiceName;

  private String myRemoteProjectPath;

  public DockerCredentialsHolder() {
  }

  public DockerCredentialsHolder(String machineName,
                                 String imageName,
                                 String containerName,
                                 String remoteProjectPath) {
    myMachineName = machineName;
    myImageName = imageName;
    myContainerName = containerName;
    myRemoteProjectPath = remoteProjectPath;
  }

  public DockerCredentialsHolder(String machineName,
                                 String composeFilePath,
                                 String composeServiceName,
                                 String imageName,
                                 String containerName,
                                 String remoteProjectPath) {
    myMachineName = machineName;
    myComposeFilePath = composeFilePath;
    myComposeServiceName = composeServiceName;
    myImageName = imageName;
    myContainerName = containerName;
    myRemoteProjectPath = remoteProjectPath;
  }

  public String getMachineName() {
    return myMachineName;
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

  public String getComposeFilePath() {
    return myComposeFilePath;
  }

  public String getComposeServiceName() {
    return myComposeServiceName;
  }

  public void save(@NotNull Element element) {
    setAttributeIfNotEmpty(element, DOCKER_MACHINE_NAME, myMachineName);
    setAttributeIfNotEmpty(element, DOCKER_IMAGE_NAME, myImageName);
    setAttributeIfNotEmpty(element, DOCKER_CONTAINER_NAME, myContainerName);
    setAttributeIfNotEmpty(element, DOCKER_COMPOSE_FILE_PATH, myComposeFilePath);
    setAttributeIfNotEmpty(element, DOCKER_COMPOSE_SERVICE_NAME, myComposeServiceName);
    element.setAttribute(DOCKER_REMOTE_PROJECT_PATH, myRemoteProjectPath);
  }

  private static void setAttributeIfNotEmpty(@NotNull Element element, @NotNull String attribute, @Nullable String value) {
    if (StringUtil.isNotEmpty(value)) {
      element.setAttribute(attribute, value);
    }
  }

  public void load(@NotNull Element element) {
    myMachineName = element.getAttributeValue(DOCKER_MACHINE_NAME);
    myImageName = element.getAttributeValue(DOCKER_IMAGE_NAME);
    myContainerName = element.getAttributeValue(DOCKER_CONTAINER_NAME);
    myComposeFilePath = element.getAttributeValue(DOCKER_COMPOSE_FILE_PATH);
    myComposeServiceName = element.getAttributeValue(DOCKER_COMPOSE_SERVICE_NAME);
    myRemoteProjectPath = element.getAttributeValue(DOCKER_REMOTE_PROJECT_PATH);
  }

  public boolean isDockerComposeCredentials() {
    return myComposeFilePath != null;
  }

  @NotNull
  public static DockerCredentialsHolder newDockerComposeCredentials(@Nullable String machineName,
                                                                    @Nullable String composeFilePath,
                                                                    @Nullable String composeServiceName,
                                                                    @Nullable String remoteProjectPath) {
    return new DockerCredentialsHolder(machineName, composeFilePath, composeServiceName, null, null, remoteProjectPath);
  }
}
