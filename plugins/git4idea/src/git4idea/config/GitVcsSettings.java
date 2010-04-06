/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package git4idea.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;

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
   * Default SSH policy
   */
  private static final SshExecutable DEFAULT_SSH = SshExecutable.IDEA_SSH;
  /**
   * the default cygwin executable
   */
  @NonNls private static final String[] DEFAULT_WINDOWS_PATHS =
    {"C:\\cygwin\\bin", "C:\\Program Files\\Git\\bin", "C:\\Program Files (x86)\\Git\\bin"};
  /**
   * Windows executable name
   */
  @NonNls private static final String DEFAULT_WINDOWS_GIT = "git.exe";
  /**
   * Default UNIX paths
   */
  @NonNls private static final String[] DEFAULT_UNIX_PATHS = {"/usr/local/bin", "/usr/bin", "/opt/local/bin", "/opt/bin"};
  /**
   * UNIX executable name
   */
  @NonNls private static final String DEFAULT_UNIX_GIT = "git";

  /**
   * The default executable for GIT
   */
  public String GIT_EXECUTABLE = defaultGit();
  /**
   * The previously entered authors of the commit (up to {@value #PREVIOUS_COMMIT_AUTHORS_LIMIT})
   */
  public String[] PREVIOUS_COMMIT_AUTHORS = {};
  /**
   * Limit for previous commit authors
   */
  public static final int PREVIOUS_COMMIT_AUTHORS_LIMIT = 16;
  /**
   * Checkout includes tags
   */
  public Boolean CHECKOUT_INCLUDE_TAGS;
  /**
   * IDEA SSH should be used instead of native SSH.
   */
  public SshExecutable SSH_EXECUTABLE = DEFAULT_SSH;
  /**
   * True if stash/unstash operation should be performed before update.
   */
  public boolean UPDATE_STASH = true;
  /**
   * The policy that specifies how files are saved before update or rebase
   */
  public UpdateChangesPolicy UPDATE_CHANGES_POLICY = null;
  /**
   * The type of update operation to perform
   */
  public UpdateType UPDATE_TYPE = UpdateType.BRANCH_DEFAULT;
  /**
   * The crlf conversion policy
   */
  public ConversionPolicy LINE_SEPARATORS_CONVERSION = ConversionPolicy.PROJECT_LINE_SEPARATORS;
  /**
   * If true, the dialog is shown with conversion options
   */
  public boolean LINE_SEPARATORS_CONVERSION_ASK = true;
  /**
   * The policy used in push active branches dialog
   */
  public UpdateChangesPolicy PUSH_ACTIVE_BRANCHES_REBASE_SAVE_POLICY = UpdateChangesPolicy.STASH;

  /**
   * @return get (a possibly converted value) of update stash policy
   */
  @NotNull
  public UpdateChangesPolicy updateChangesPolicy() {
    if (UPDATE_CHANGES_POLICY == null) {
      UPDATE_CHANGES_POLICY = UPDATE_STASH ? UpdateChangesPolicy.STASH : UpdateChangesPolicy.KEEP;
    }
    return UPDATE_CHANGES_POLICY;
  }

  /**
   * Save an author of the commit and make it the first one. If amount of authors exceeds the limit, remove least recently selected author.
   *
   * @param author an author to save
   */
  public void saveCommitAuthor(String author) {
    LinkedList<String> authors = new LinkedList<String>(Arrays.asList(PREVIOUS_COMMIT_AUTHORS));
    authors.remove(author);
    while (authors.size() >= PREVIOUS_COMMIT_AUTHORS_LIMIT) {
      authors.removeLast();
    }
    authors.addFirst(author);
    PREVIOUS_COMMIT_AUTHORS = ArrayUtil.toStringArray(authors);
  }

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
   * @return the git settings
   */
  public static GitVcsSettings getInstance(Project project) {
    return ServiceManager.getService(project, GitVcsSettings.class);
  }

  /**
   * Get instance with checked read action
   *
   * @param project the project to get setting for
   * @return the settings object
   */
  public static GitVcsSettings getInstanceChecked(final Project project) {
    return ApplicationManager.getApplication().runReadAction(new Computable<GitVcsSettings>() {
      public GitVcsSettings compute() {
        if (project.isDisposed()) throw new ProcessCanceledException();
        return ServiceManager.getService(project, GitVcsSettings.class);
      }
    });
  }

  /**
   * @return the default executable name depending on the platform
   */
  private static String defaultGit() {
    String[] paths;
    String program;
    if (SystemInfo.isWindows) {
      program = DEFAULT_WINDOWS_GIT;
      paths = DEFAULT_WINDOWS_PATHS;
    }
    else {
      program = DEFAULT_UNIX_GIT;
      paths = DEFAULT_UNIX_PATHS;
    }
    for (String p : paths) {
      File f = new File(p, program);
      if (f.exists()) {
        return f.getAbsolutePath();
      }
    }
    return program;     // otherwise, hope it's in $PATH
  }

  /**
   * @return true if IDEA ssh should be used
   */
  public boolean isIdeaSsh() {
    return (SSH_EXECUTABLE == null ? DEFAULT_SSH : SSH_EXECUTABLE) == SshExecutable.IDEA_SSH;
  }

  /**
   * @return true if IDEA ssh should be used
   */
  public static boolean isDefaultIdeaSsh() {
    return DEFAULT_SSH == SshExecutable.IDEA_SSH;
  }

  /**
   * Set IDEA ssh value
   *
   * @param value the value to set
   */
  public void setIdeaSsh(boolean value) {
    SSH_EXECUTABLE = value ? SshExecutable.IDEA_SSH : SshExecutable.NATIVE_SSH;
  }

  /**
   * The way the local changes are saved before update if user has selected auto-stash
   */
  public enum UpdateChangesPolicy {
    /**
     * Stash changes
     */
    STASH,
    /**
     * Shelve changes
     */
    SHELVE,
    /**
     * Keep files in working tree
     */
    KEEP
  }

  /**
   * Kinds of SSH executable to be used with the git
   */
  public enum SshExecutable {
    /**
     * SSH provided by IDEA
     */
    IDEA_SSH,
    /**
     * Naive SSH.
     */
    NATIVE_SSH, }

  /**
   * The type of update to perform
   */
  public enum UpdateType {
    /**
     * Use default specified in the config file for the branch
     */
    BRANCH_DEFAULT,
    /**
     * Merge fetched commits with local branch
     */
    MERGE,
    /**
     * Rebase local commits upon the fetched branch
     */
    REBASE
  }

  /**
   * The CRLF conversion policy
   */
  public enum ConversionPolicy {
    /**
     * No conversion is performed
     */
    NONE,
    /**
     * The files are converted to project line separators
     */
    PROJECT_LINE_SEPARATORS
  }
}
