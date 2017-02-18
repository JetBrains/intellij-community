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
package git4idea.config;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tries to detect the path to Git executable.
 *
 * @author Kirill Likhodedov
 */
public class GitExecutableDetector {

  private static final Logger LOG = Logger.getInstance(GitExecutableDetector.class);
  private static final String[] UNIX_PATHS = { "/usr/local/bin",
                                               "/usr/bin",
                                               "/opt/local/bin",
                                               "/opt/bin",
                                               "/usr/local/git/bin"};
  private static final String UNIX_EXECUTABLE = "git";

  private static final File WIN_ROOT = new File("C:"); // the constant is extracted to be able to create files in "Program Files" in tests
  private static final String GIT_CMD = "git.cmd";
  private static final String GIT_EXE = "git.exe";

  public static final String DEFAULT_WIN_GIT = GIT_EXE;
  public static final String PATH_ENV = "PATH";

  @NotNull
  public String detect() {
    if (SystemInfo.isWindows) {
      return detectForWindows();
    }
    return detectForUnix();
  }

  @NotNull
  private static String detectForUnix() {
    for (String p : UNIX_PATHS) {
      File f = new File(p, UNIX_EXECUTABLE);
      if (f.exists()) {
        return f.getPath();
      }
    }
    return UNIX_EXECUTABLE;
  }

  @NotNull
  private String detectForWindows() {
    String exec = checkInPath();
    if (exec != null) {
      return exec;
    }

    exec = checkProgramFiles();
    if (exec != null) {
      return exec;
    }

    exec = checkCygwin();
    if (exec != null) {
      return exec;
    }

    return checkSoleExecutable();
  }

  /**
   * Looks into the %PATH% and checks Git directories mentioned there.
   * @return Git executable to be used or null if nothing interesting was found in the PATH.
   */
  @Nullable
  private String checkInPath() {
    String PATH = getPath();
    if (PATH == null) {
      return null;
    }
    List<String> pathEntries = StringUtil.split(PATH, ";");
    for (String pathEntry : pathEntries) {
      if (looksLikeGit(pathEntry)) {
        return checkBinDir(new File(pathEntry));
      }
    }
    return null;
  }

  private static boolean looksLikeGit(@NotNull String path) {
    List<String> dirs = FileUtil.splitPath(path);
    for (String dir : dirs) {
      if (dir.toLowerCase().startsWith("git")) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static String checkProgramFiles() {
    final String[] PROGRAM_FILES = { "Program Files", "Program Files (x86)" };

    // collecting all potential msys distributives
    List<File> distrs = new ArrayList<>();
    for (String programFiles : PROGRAM_FILES) {
      File pf = new File(WIN_ROOT, programFiles);
      File[] children = pf.listFiles(new FileFilter() {
        @Override
        public boolean accept(File pathname) {
          return pathname.isDirectory() && pathname.getName().toLowerCase().startsWith("git");
        }
      });
      if (!pf.exists() || children == null) {
        continue;
      }
      distrs.addAll(Arrays.asList(children));
    }

    // greater is better => sorting in the descending order to match the best version first, when iterating
    Collections.sort(distrs, Collections.reverseOrder(new VersionDirsComparator()));

    for (File distr : distrs) {
      String exec = checkDistributive(distr);
      if (exec != null) {
        return exec;
      }
    }
    return null;
  }

  @Nullable
  private static String checkCygwin() {
    final String[] OTHER_WINDOWS_PATHS = { FileUtil.toSystemDependentName("cygwin/bin/git.exe") };
    for (String otherPath : OTHER_WINDOWS_PATHS) {
      File file = new File(WIN_ROOT, otherPath);
      if (file.exists()) {
        return file.getPath();
      }
    }
    return null;
  }

  @NotNull
  private String checkSoleExecutable() {
    if (runs(GIT_CMD)) {
      return GIT_CMD;
    }
    return GIT_EXE;
  }

  @Nullable
  private static String checkDistributive(@Nullable File gitDir) {
    if (gitDir == null || !gitDir.exists()) {
      return null;
    }

    final String[] binDirs = { "cmd", "bin" };
    for (String binDir : binDirs) {
      String exec = checkBinDir(new File(gitDir, binDir));
      if (exec != null) {
        return exec;
      }
    }

    return null;
  }

  @Nullable
  private static String checkBinDir(@NotNull File binDir) {
    if (!binDir.exists()) {
      return null;
    }

    for (String exec : new String[]{ GIT_CMD, GIT_EXE }) {
      File fe = new File(binDir, exec);
      if (fe.exists()) {
        return fe.getPath();
      }
    }

    return null;
  }

  /**
   * Checks if it is possible to run the specified program.
   * Made protected for tests not to start a process there.
   */
  protected boolean runs(@NotNull String exec) {
    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(exec);
    commandLine.setCharset(CharsetToolkit.getDefaultSystemCharset());
    try {
      CapturingProcessHandler handler = new CapturingProcessHandler(commandLine);
      ProcessOutput result = handler.runProcess((int)TimeUnit.SECONDS.toMillis(5));
      return !result.isTimeout();
    }
    catch (ExecutionException e) {
      return false;
    }
  }

  @Nullable
  protected String getPath() {
    return System.getenv(PATH_ENV);
  }

  // Compare strategy: greater is better (if v1 > v2, then v1 is a better candidate for the Git executable)
  private static class VersionDirsComparator implements Comparator<File> {

    @Override
    public int compare(File f1, File f2) {
      String name1 = f1.getName().toLowerCase();
      String name2 = f2.getName().toLowerCase();

      // C:\Program Files\Git is better candidate for _default_ than C:\Program Files\Git_1.8.0
      if (name1.equals("git")) {
        return name2.equals("git") ? fallback(f1, f2) : 1;
      }
      else if (name2.equals("git")) {
        return -1;
      }

      final Pattern GIT_WITH_VERSION = Pattern.compile("^git[ _]*([\\d\\.]*).*$");
      Matcher m1 = GIT_WITH_VERSION.matcher(name1);
      Matcher m2 = GIT_WITH_VERSION.matcher(name2);
      if (m1.matches() && m2.matches()) {
        GitVersion v1 = parseGitVersion(m1.group(1));
        GitVersion v2 = parseGitVersion(m2.group(1));
        if (v1 == null || v2 == null) {
          return fallback(f1, f2);
        }
        int compareVersions = v1.compareTo(v2);
        return compareVersions == 0 ? fallback(f1, f2) : compareVersions;
      }
      return fallback(f1, f2);
    }

    private static int fallback(@NotNull File f1, @NotNull File f2) {
      // "Program Files" is preferable over "Program Files (x86)"
      int compareParents = f1.getParentFile().getName().compareTo(f2.getParentFile().getName());
      if (compareParents != 0) {
        return -compareParents; // greater is better => reversing
      }

      // probably some unrecognized format of Git directory naming => just compare lexicographically
      String name1 = f1.getName().toLowerCase();
      String name2 = f2.getName().toLowerCase();
      return name1.compareTo(name2);
    }

    // not using GitVersion#parse(), because it requires at least 3 items in the version (1.7.3),
    // and parses the `git version` command output, not just the version string.
    @Nullable
    private static GitVersion parseGitVersion(@Nullable String name) {
      if (name == null) {
        return null;
      }
      final Pattern VERSION = Pattern.compile("(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:\\.(\\d+))?.*");
      Matcher m = VERSION.matcher(name);
      if (!m.matches()) {
        return null;
      }
      try {
        int major = Integer.parseInt(m.group(1));
        return new GitVersion(major, parseOrNull(m.group(2)), parseOrNull(m.group(3)), parseOrNull(m.group(4)));
      }
      catch (NumberFormatException e) {
        LOG.info("Unexpected NFE when parsing [" + name + "]", e);
        return null;
      }
    }

    private static int parseOrNull(String group) {
      return group == null ? 0 : Integer.parseInt(group);
    }
  }
}
