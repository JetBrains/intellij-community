/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.execution.ExecutableValidator;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NotNull;

import java.text.ParseException;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The version of Git. Note that the version number ignores build and commit hash.
 * The class is able to distinct MSYS and CYGWIN Gits under Windows assuming that msysgit adds the 'msysgit' suffix to the output
 * of the 'git version' command.
 * This is not a very good way to distinguish msys and cygwin since in old versions of msys they didn't add the suffix.
 *
 * Note: this class has a natural ordering that is inconsistent with equals.
 */
public final class GitVersion implements Comparable<GitVersion> {

  /**
   * Type indicates the type of this git distribution: is it native (unix) or msys or cygwin.
   * Type UNDEFINED means that the type doesn't matter in certain condition.
   */
  public enum Type {
    UNIX,
    MSYS,
    CYGWIN,
    /** The type doesn't matter or couldn't be detected. */
    UNDEFINED,
    /**
     * Information about Git version is unavailable because the GitVcs hasn't fully initialized yet, or because Git executable is invalid.
     */
    NULL
  }

  /**
   * The minimal supported version
   */
  public static final GitVersion MIN = new GitVersion(1, 7, 1, 1);

  /**
   * Special version with a special Type which indicates, that Git version information is unavailable.
   * Probably, because of invalid executable, or when GitVcs hasn't fully initialized yet.
   */
  public static final GitVersion NULL = new GitVersion(0, 0, 0, 0, Type.NULL);

  private static final Pattern FORMAT = Pattern.compile(
    "git version (\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(.*)", Pattern.CASE_INSENSITIVE);

  private static final Logger LOG = Logger.getInstance(GitVersion.class.getName());

  private final int myMajor;
  private final int myMinor;
  private final int myRevision;
  private final int myPatchLevel;
  @NotNull private final Type myType;

  private final int myHashCode;

  public GitVersion(int major, int minor, int revision, int patchLevel, @NotNull Type type) {
    myMajor = major;
    myMinor = minor;
    myRevision = revision;
    myPatchLevel = patchLevel;
    myType = type;
    myHashCode = Objects.hash(myMajor, myMinor, myRevision, myPatchLevel);
  }

  /**
   * Creates new GitVersion with the UNDEFINED Type which actually means that the type doesn't matter for current purpose.
   */
  public GitVersion(int major, int minor, int revision, int patchLevel) {
    this(major, minor, revision, patchLevel, Type.UNDEFINED);
  }

  /**
   * Parses output of "git version" command.
   */
  @NotNull
  public static GitVersion parse(@NotNull String output) throws ParseException {
    if (StringUtil.isEmptyOrSpaces(output)) {
      throw new ParseException("Empty git --version output: " + output, 0);
    }
    Matcher m = FORMAT.matcher(output.trim());
    if (!m.matches()) {
      throw new ParseException("Unsupported format of git --version output: " + output, 0);
    }
    int major = getIntGroup(m, 1);
    int minor = getIntGroup(m, 2);
    int rev = getIntGroup(m, 3);
    int patch = getIntGroup(m, 4);
    boolean msys = (m.groupCount() >= 5) && m.group(5) != null && m.group(5).toLowerCase().contains("msysgit");
    Type type;
    if (SystemInfo.isWindows) {
      type = msys ? Type.MSYS : Type.CYGWIN;
    } else {
      type = Type.UNIX;
    }
    return new GitVersion(major, minor, rev, patch, type);
  }

  // Utility method used in parsing - checks that the given capture group exists and captured something - then returns the captured value,
  // otherwise returns 0.
  private static int getIntGroup(@NotNull Matcher matcher, int group) {
    if (group > matcher.groupCount()+1) {
      return 0;
    }
    final String match = matcher.group(group);
    if (match == null) {
      return 0;
    }
    return Integer.parseInt(match);
  }

  @NotNull
  public static GitVersion identifyVersion(@NotNull String gitExecutable) throws TimeoutException, ExecutionException, ParseException {
    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(gitExecutable);
    commandLine.addParameter("--version");
    commandLine.setCharset(CharsetToolkit.getDefaultSystemCharset());
    CapturingProcessHandler handler = new CapturingProcessHandler(commandLine);
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    ProcessOutput result = indicator == null ?
                           handler.runProcess(ExecutableValidator.TIMEOUT_MS) :
                           handler.runProcessWithProgressIndicator(indicator);
    if (result.isTimeout()) {
      throw new TimeoutException("Couldn't identify the version of Git - stopped by timeout.");
    }
    else if (result.isCancelled()) {
      LOG.info("Cancelled by user. exitCode=" + result.getExitCode());
      throw new ProcessCanceledException();
    }
    else if (result.getExitCode() != 0 || !result.getStderr().isEmpty()) {
      LOG.info("getVersion exitCode=" + result.getExitCode() + " errors: " + result.getStderr());
      // anyway trying to parse
      try {
        parse(result.getStdout());
      } catch (ParseException pe) {
        throw new ExecutionException("Errors while executing git --version. exitCode=" + result.getExitCode() +
                                     " errors: " + result.getStderr());
      }
    }
    return parse(result.getStdout());
  }

  /**
   * @return true if the version is supported by the plugin
   */
  public boolean isSupported() {
    return getType() != Type.NULL && compareTo(MIN) >= 0;
  }

  /**
   * Note: this class has a natural ordering that is inconsistent with equals.
   * Two GitVersions are equal if their number versions are equal and if their types are equal.
   * Types are considered equal also if one of them is undefined. Otherwise they are compared.
   */
  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof GitVersion)) {
      return false;
    }
    GitVersion other = (GitVersion) obj;
    if (compareTo(other) != 0) {
      return false;
    }
    if (myType == Type.UNDEFINED || other.myType == Type.UNDEFINED) {
      return true;
    }
    return myType == other.myType;
  }

  /**
   * Hashcode is computed from numbered components of the version. Thus GitVersions with the same numbered components will be compared
   * by equals, and there the type will be taken into consideration).
   */
  @Override
  public int hashCode() {
    return myHashCode;
  }

  /**
   * Note: this class has a natural ordering that is inconsistent with equals.
   * Only numbered versions are compared, so
   * (msys git 1.7.3).compareTo(cygwin git 1.7.3) == 0
   * BUT
   * (msys git 1.7.3).equals(cygwin git 1.7.3) == false
   * 
   * {@link GitVersion#NULL} is less than any other not-NULL version. 
   */
  public int compareTo(@NotNull GitVersion o) {
    if (o.getType() == Type.NULL) {
      return (getType() == Type.NULL ? 0 : 1);
    }
    int d = myMajor - o.myMajor;
    if (d != 0) {
      return d;
    }
    d = myMinor - o.myMinor;
    if (d != 0) {
      return d;
    }
    d = myRevision - o.myRevision;
    if (d != 0) {
      return d;
    }
    return myPatchLevel - o.myPatchLevel;
  }

  @Override
  public String toString() {
    final String msysIndicator = (myType == Type.MSYS ? ".msysgit" : "");
    return myMajor + "." + myMinor + "." + myRevision + "." + myPatchLevel + msysIndicator;
  }

  /**
   * @return true if this version is older or the same than the given one.
   */
  public boolean isOlderOrEqual(final GitVersion gitVersion) {
    return gitVersion != null && compareTo(gitVersion) <= 0;
  }

  /**
   * @return true if this version is later or the same than the given one.
   */
  public boolean isLaterOrEqual(GitVersion version) {
    return version != null && compareTo(version) >= 0;
  }

  @NotNull
  public Type getType() {
    return myType;
  }

  public boolean isNull() {
    return getType() == Type.NULL;
  }

}
