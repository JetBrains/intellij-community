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

import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author michael.golubev
 */
public class CloudGitDeploymentDetector {

  private final Pattern myGitUrlPattern;

  public CloudGitDeploymentDetector(Pattern gitUrlPattern) {
    myGitUrlPattern = gitUrlPattern;
  }

  public List<String> collectApplicationNames(@NotNull GitRepository repository) {
    List<String> result = new ArrayList<String>();
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
}
