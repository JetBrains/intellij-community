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
package org.zmlx.hg4idea.util;

import com.google.common.base.Objects;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.ShellCommandException;

import java.text.ParseException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The version of Hg.
 * Note: this class has a natural ordering that is inconsistent with equals.
 */
public final class HgVersion implements Comparable<HgVersion> {

  private static final Logger LOGGER = Logger.getInstance(HgVersion.class);
  private static final Pattern HG_VERSION_PATTERN =
    Pattern.compile(".+\\(\\s*\\S+\\s+(\\d+)\\.(\\d+)[\\.]?(\\d)?.*\\s*\\)\\s*.*\\s*", Pattern.CASE_INSENSITIVE);
  //f.e. Mercurial Distributed SCM (version 2.6+20130507) or Mercurial Distributed SCM (version 2.6.2), 2.7-rc+5-ca2dfc2f63eb
  private static final Pattern HG_UNSUPPORTED_EXTENSION =
    Pattern.compile("\\*\\*\\*\\s*failed to import\\s+extension\\s+([a-zA-z0-9\\.-]+).*", Pattern.CASE_INSENSITIVE);
  /**
   * The minimal supported version
   */
  public static final HgVersion MIN = new HgVersion(2, 0, 1);
  public static final HgVersion AMEND_SUPPORTED = new HgVersion(2, 2, 0);

  // before 2.3 build in func not supported
  // since 2.3 - 2.5.3 hg has bug with join function with file_copies
  // see http://mercurial.808500.n3.nabble.com/Bug-3887-New-hg-log-template-quot-rev-join-file-copies-n-quot-prints-literal-quot-sourcename-quot-fos-td4000129.html
  public static final HgVersion BUILT_IN_FUNCTION_SUPPORTED = new HgVersion(2, 6, 0);
  public static final HgVersion PARENT_REVISION_TEMPLATES_SUPPORTED = new HgVersion(2, 4, 0);
  public static final HgVersion BRANCH_HEADS_BASE_SERVED_FILE_EXIST = new HgVersion(2, 5, 0);
  public static final HgVersion BRANCH2_FILE_EXIST = new HgVersion(2, 9, 0);
  public static final HgVersion IGNORE_WHITESPACE_DIFF_IN_ANNOTATIONS = new HgVersion(2, 1, 0);

  //see http://selenic.com/pipermail/mercurial-devel/2013-May/051209.html  fixed since 2.7
  private static final HgVersion LARGEFILES_WITH_FOLLOW_SUPPORTED = new HgVersion(2, 7, 0);

  /**
   * Special version which indicates, that Hg version information is unavailable.
   */
  public static final HgVersion NULL = new HgVersion(0, 0, 0);

  private final int myMajor;
  private final int myMiddle;
  private final int myMinor;  //use only first digit after second dot
  @NotNull private final Set<String> myUnsupportedExtensions;

  public HgVersion(int major, int middle, int minor) {
    this(major, middle, minor, Collections.<String>emptySet());
  }

  public HgVersion(int major, int middle, int minor, @NotNull Set<String> unsupportedExtensions) {
    myMajor = major;
    myMiddle = middle;
    myMinor = minor;
    myUnsupportedExtensions = unsupportedExtensions;
  }

  /**
   * Parses output of "Hg version" command.
   */
  @NotNull
  public static HgVersion parseVersionAndExtensionInfo(@Nullable String output,
                                                       @NotNull List<String> errorLines)
    throws ParseException {
    if (StringUtil.isEmptyOrSpaces(output)) {
      throw new ParseException("Empty hg version output: " + output, 0);
    }
    Matcher matcher = HG_VERSION_PATTERN.matcher(output);
    if (matcher.matches()) {
      return new HgVersion(getIntGroup(matcher, 1), getIntGroup(matcher, 2), getIntGroup(matcher, 3),
                           parseUnsupportedExtensions(errorLines));
    }
    LOGGER.error("Couldn't identify hg version: " + output);
    throw new ParseException("Unsupported format of hg version output: " + output, 0);
  }

  @NotNull
  public static Set<String> parseUnsupportedExtensions(@NotNull List<String> errorLines) {
    // hg version command execute with null start directory,
    // but hgrc configuration file may be related to one of repository then extension may be failed to import too
    //before fixed use command exit value instead if errors.isEmpty
    //todo store all unsupported extensions for all repository and notify once
    Set<String> extensions = new HashSet<>();
    if (errorLines.isEmpty()) {
      return extensions;
    }
    for (String line : errorLines) {
      Matcher matcher = HG_UNSUPPORTED_EXTENSION.matcher(line);
      if (matcher.matches()) {
        extensions.add(matcher.group(1));
      }
    }
    return extensions;
  }

  // Utility method used in parsing - checks that the given capture group exists and captured something - then returns the captured value,
  // otherwise returns 0.
  private static int getIntGroup(@NotNull Matcher matcher, int group) {

    if (group > matcher.groupCount() + 1) {
      return 0;
    }
    final String match = matcher.group(group);
    if (StringUtil.isEmptyOrSpaces(match)) {
      return 0;
    }
    return Integer.parseInt(match);
  }

  @NotNull
  public static HgVersion identifyVersion(@NotNull String executable)
    throws ShellCommandException, InterruptedException, ParseException {
    HgCommandResult versionResult = HgUtil.getVersionOutput(executable);
    return parseVersionAndExtensionInfo(versionResult.getRawOutput(), versionResult.getErrorLines());
  }

  /**
   * @return true if the version is supported by the plugin
   */
  public boolean isSupported() {
    return compareTo(MIN) >= 0;
  }

  public boolean isAmendSupported() {
    return compareTo(AMEND_SUPPORTED) >= 0;
  }

  public boolean isBuiltInFunctionSupported() {
    return compareTo(BUILT_IN_FUNCTION_SUPPORTED) >= 0;
  }

  public boolean isLargeFilesWithFollowSupported() {
    return compareTo(LARGEFILES_WITH_FOLLOW_SUPPORTED) >= 0;
  }

  public boolean isParentRevisionTemplateSupported() {
    return compareTo(PARENT_REVISION_TEMPLATES_SUPPORTED) >= 0;
  }

  public boolean isIgnoreWhitespaceDiffInAnnotationsSupported() {
    return compareTo(IGNORE_WHITESPACE_DIFF_IN_ANNOTATIONS) >= 0;
  }

  public boolean hasBranchHeadsBaseServed() {
    return compareTo(BRANCH_HEADS_BASE_SERVED_FILE_EXIST) >= 0 && compareTo(BRANCH2_FILE_EXIST) < 0;
  }

  public boolean hasBranch2() {
    return compareTo(BRANCH2_FILE_EXIST) >= 0;
  }

  public boolean hasUnsupportedExtensions() {
    return !myUnsupportedExtensions.isEmpty();
  }

  @NotNull
  public Set<String> getUnsupportedExtensions() {
    return myUnsupportedExtensions;
  }

  /**
   * Note: this class has a natural ordering that is inconsistent with equals.
   * Two HgVersions are equal if their number versions are equal.
   */
  @Override
  public boolean equals(final Object obj) {
    if (!(obj instanceof HgVersion)) {
      return false;
    }
    return compareTo((HgVersion)obj) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myMajor, myMiddle, myMinor);
  }

  /**
   * Note: this class has a natural ordering that is inconsistent with equals.
   * Only numbered versions are compared, so
   * (Hg 1.7.3).compareTo(1.7.3) == 0
   * <p/>
   * {@link HgVersion#NULL} is less than any other not-NULL version.
   */
  public int compareTo(@NotNull HgVersion o) {
    int d = myMajor - o.myMajor;
    if (d != 0) {
      return d;
    }
    d = myMiddle - o.myMiddle;
    if (d != 0) {
      return d;
    }
    return myMinor - o.myMinor;
  }

  @Override
  @NotNull
  public String toString() {
    return myMajor + "." + myMiddle + "." + myMinor;
  }
}
