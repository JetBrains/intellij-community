package git4idea.config;
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

import java.io.File;

/**
 * Git VCS settings
 */
@State(
    name = "Git.Settings",
    storages = {@Storage(
        id = "ws",
        file = "$WORKSPACE_FILE$")})
public class GitVcsSettings implements PersistentStateComponent<GitVcsSettings> {
  /**
   * the default cygwin executable
   */
  @NonNls private static final String[] DEFAULT_WINDOWS_PATHS = {"C:\\cygwin\\bin", "C:\\Program Files\\Git\\bin"};
  /**
   * Windows executable name
   */
  @NonNls private static final String DEFAULT_WINDOWS_GIT = "git.exe";
  /**
   * Default unix paths
   */
  @NonNls private static final String[] DEFAULT_UNIX_PATHS = {"/usr/local/bin", "/usr/bin", "/opt/local/bin", "/opt/bin"};
  /**
   * Unix executable name
   */
  @NonNls private static final String DEFAULT_UNIX_GIT = "git";

  /**
   * The default executable for GIT
   */
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

  /**
   * Get git setting for the project
   *
   * @param project a context project
   * @return the git settigns
   */
  public static GitVcsSettings getInstance(Project project) {
    return ServiceManager.getService(project, GitVcsSettings.class);
  }

  /**
   * @return the default executable name depending on the platform
   */
  private static String defaultGit() {
    String[] paths;
    String exe;
    if (SystemInfo.isWindows) {
      exe = DEFAULT_WINDOWS_GIT;
      paths = DEFAULT_WINDOWS_PATHS;
    }
    else {
      exe = DEFAULT_UNIX_GIT;
      paths = DEFAULT_UNIX_PATHS;
    }
    for (String p : paths) {
      File f = new File(p, exe);
      if (f.exists()) {
        return f.getAbsolutePath();
      }
    }
    return exe;     // otherwise, hope it's in $PATH
  }
}
