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
package com.intellij.remoteServer.util;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.remoteServer.ServerType;
import com.intellij.util.containers.ContainerUtil;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author michael.golubev
 */
public abstract class CloudGitDeploymentDetector {

  public static final ExtensionPointName<CloudGitDeploymentDetector> EP_NAME
    = ExtensionPointName.create("com.intellij.remoteServer.util.deploymentDetector");

  public static CloudGitDeploymentDetector getInstance(ServerType cloudType) {
    for (CloudGitDeploymentDetector deploymentDetector : EP_NAME.getExtensions()) {
      if (deploymentDetector.getCloudType() == cloudType) {
        return deploymentDetector;
      }
    }
    throw new IllegalArgumentException("Deployment detector is not registered for: " + cloudType.getPresentableName());
  }

  private final Pattern myGitUrlPattern;

  protected CloudGitDeploymentDetector(Pattern gitUrlPattern) {
    myGitUrlPattern = gitUrlPattern;
  }

  @Nullable
  public String getFirstApplicationName(@NotNull GitRepository repository) {
    return ContainerUtil.getFirstItem(collectApplicationNames(repository));
  }

  public List<String> collectApplicationNames(@NotNull GitRepository repository) {
    List<String> result = new ArrayList<>();
    for (GitRemote remote : repository.getRemotes()) {
      for (String url : remote.getUrls()) {
        Matcher matcher = myGitUrlPattern.matcher(url);
        if (matcher.matches()) {
          result.add(matcher.group(1));
        }
      }
    }
    return result;
  }

  public abstract ServerType getCloudType();

  public abstract CloudDeploymentNameConfiguration createDeploymentConfiguration();
}
