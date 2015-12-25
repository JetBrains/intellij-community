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
public class DockerComposeCredentialsHolder {
  public static final String DOCKER_MACHINE_NAME = "DOCKER_MACHINE_NAME";
  public static final String DOCKER_COMPOSE_FILE_PATH = "DOCKER_COMPOSE_FILE_PATH";
  public static final String DOCKER_COMPOSE_SERVICE_NAME = "DOCKER_COMPOSE_SERVICE_NAME";
  public static final String DOCKER_REMOTE_PROJECT_PATH = "DOCKER_REMOTE_PROJECT_PATH";

  private String myMachineName;

  private String myComposeFilePath;

  private String myComposeServiceName;

  private String myRemoteProjectPath;

  public DockerComposeCredentialsHolder() {
  }

  public DockerComposeCredentialsHolder(@Nullable String machineName,
                                        @Nullable String composeFilePath,
                                        @Nullable String composeServiceName,
                                        @Nullable String remoteProjectPath) {
    myMachineName = machineName;
    myComposeFilePath = composeFilePath;
    myComposeServiceName = composeServiceName;
    myRemoteProjectPath = remoteProjectPath;
  }

  public String getMachineName() {
    return myMachineName;
  }

  public String getComposeFilePath() {
    return myComposeFilePath;
  }

  public String getComposeServiceName() {
    return myComposeServiceName;
  }

  public String getRemoteProjectPath() {
    return myRemoteProjectPath;
  }

  public void save(@NotNull Element element) {
    if (StringUtil.isNotEmpty(myMachineName)) {
      element.setAttribute(DOCKER_MACHINE_NAME, myMachineName);
    }
    element.setAttribute(DOCKER_COMPOSE_FILE_PATH, myComposeFilePath);
    element.setAttribute(DOCKER_COMPOSE_SERVICE_NAME, myComposeServiceName);
  }

  public void load(@NotNull Element element) {
    myMachineName = element.getAttributeValue(DOCKER_MACHINE_NAME);
    myComposeFilePath = element.getAttributeValue(DOCKER_COMPOSE_FILE_PATH);
    myComposeServiceName = element.getAttributeValue(DOCKER_COMPOSE_SERVICE_NAME);
    myRemoteProjectPath = element.getAttributeValue(DOCKER_REMOTE_PROJECT_PATH);
  }
}
