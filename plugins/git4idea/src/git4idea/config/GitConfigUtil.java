/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.GitSimpleHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Git utilities for working with configuration
 */
public class GitConfigUtil {
  /**
   * A private constructor for utility class
   */
  private GitConfigUtil() {
  }

  /**
   * Get configuration values for the repository. Note that the method executes a git command.
   *
   * @param project the context project
   * @param root    the git root
   * @param keyMask the keys to be queried
   * @param result  the map to put results to
   * @throws VcsException an exception
   */
  public static void getValues(Project project, VirtualFile root, String keyMask, Map<String, String> result) throws VcsException {
    GitSimpleHandler h = new GitSimpleHandler(project, root, "config");
    h.setNoSSH(true);
    h.setSilent(true);
    h.addParameters("--null", "--get-regexp", keyMask);
    String output = h.run();
    int start = 0;
    int pos;
    while ((pos = output.indexOf('\n', start)) != -1) {
      String key = output.substring(start, pos);
      start = pos + 1;
      if ((pos = output.indexOf('\u0000', start)) == -1) {
        break;
      }
      String value = output.substring(start, pos);
      start = pos + 1;
      result.put(key, value);
    }
  }

  /**
   * Get configuration values for the repository. Note that the method executes a git command.
   *
   * @param project the context project
   * @param root    the git root
   * @param key     the keys to be queried
   * @return list of pairs ({@link Pair#first} is the key, {@link Pair#second} is the value)
   * @throws VcsException an exception
   */
  public static List<Pair<String, String>> getAllValues(Project project, VirtualFile root, @NonNls String key) throws VcsException {
    List<Pair<String, String>> result = new ArrayList<Pair<String, String>>();
    GitSimpleHandler h = new GitSimpleHandler(project, root, "config");
    h.setNoSSH(true);
    h.setSilent(true);
    h.addParameters("--null", "--get-all", key);
    String output = h.run();
    int start = 0;
    int pos;
    while ((pos = output.indexOf('\u0000', start)) != -1) {
      String value = output.substring(start, pos);
      start = pos + 1;
      result.add(new Pair<String, String>(key, value));
    }
    return result;
  }


  /**
   * Get configuration value for the repository. Note that the method executes a git command.
   *
   * @param project the context project
   * @param root    the git root
   * @param key     the keys to be queried
   * @return the value associtated with the key or null if the value is not found
   * @throws VcsException an exception
   */
  @Nullable
  public static String getValue(Project project, VirtualFile root, @NonNls String key) throws VcsException {
    GitSimpleHandler h = new GitSimpleHandler(project, root, "config");
    h.setNoSSH(true);
    h.setSilent(true);
    h.ignoreErrorCode(1);
    h.addParameters("--null", "--get", key);
    String output = h.run();
    int pos = output.indexOf('\u0000');
    if (h.getExitCode() != 0 || pos == -1) {
      return null;
    }
    return output.substring(0, pos);
  }
}
