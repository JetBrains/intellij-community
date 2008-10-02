package git4idea;
/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 *
 * Copyright 2007 Decentrix Inc
 * Copyright 2007 Aspiro AS
 * Copyright 2008 MQSoftware
 * Copyright 2008 JetBrains s.r.o.
 * 
 * Authors: gevession, Erlend Simonsen & Mark Scott
 *
 * This code was originally derived from the MKS & Mercurial IDEA VCS plugins
 */

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NonNls;

/**
 * Git VCS settings
 */
@State(
    name = "Git.Settings",
    storages = {@Storage(
        id = "ws",
        file = "$WORKSPACE_FILE$")})
public class GitVcsSettings implements PersistentStateComponent<GitVcsSettings> {
  @NonNls private static final String DEFAULT_WIN_GIT_EXEC = "C:\\cygwin\\bin\\git.exe";
  @NonNls private static final String DEFAULT_MAC_GIT_EXEC = "/usr/local/bin/git";
  @NonNls private static final String DEFAULT_UNIX_GIT_EXEC = "git";
  public String GIT_EXECUTABLE = defaultGit();

  /**
   * {@inheritDoc}
   */
  public GitVcsSettings getState() {
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public void loadState(GitVcsSettings gitVcsSettings) {
    XmlSerializerUtil.copyBean(gitVcsSettings, this);
  }

  public static GitVcsSettings getInstance(Project project) {
    return ServiceManager.getService(project, GitVcsSettings.class);
  }

  /**
   * @return the default executable name depending on the platform
   */
  private static String defaultGit() {
    if (SystemInfo.isWindows) return DEFAULT_WIN_GIT_EXEC;
    if (SystemInfo.isMac) return DEFAULT_MAC_GIT_EXEC;
    return DEFAULT_UNIX_GIT_EXEC;
  }
}
