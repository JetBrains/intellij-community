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
import org.zmlx.hg4idea.execution.ShellCommand;
import org.zmlx.hg4idea.execution.ShellCommandException;

import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The version of Hg.
 * Note: this class has a natural ordering that is inconsistent with equals.
 */
public final class HgVersion implements Comparable<HgVersion> {

  private static final Logger LOGGER = Logger.getInstance(HgVersion.class);
  private static final Pattern HG_VERSION_PATTERN =
    Pattern.compile(".+\\(\\s*\\S+\\s+(\\d+)\\.(\\d+)[\\+\\.-]?(\\d*)?.*\\s*\\)\\s*", Pattern.CASE_INSENSITIVE);
  //f.e. Mercurial Distributed SCM (version 2.6+20130507) or Mercurial Distributed SCM (version 2.6.2), 2.7-rc+5-ca2dfc2f63eb

  /**
   * The minimal supported version
   */
  public static final HgVersion MIN = new HgVersion(1, 9, 1);
  public static final HgVersion AMENDSUPPORTED = new HgVersion(2, 2, 0);

  /**
   * Special version with a special Type which indicates, that Hg version information is unavailable.
   */
  public static final HgVersion NULL = new HgVersion(0, 0, 0);

  private final int myMajor;
  private final int myMiddle;
  private final int myMinor;

  private final int myHashCode;

  public HgVersion(int major, int middle, int minor) {
    myMajor = major;
    myMiddle = middle;
    myMinor = minor;
    myHashCode = Objects.hashCode(myMajor, myMiddle, myMinor);
  }

  /**
   * Parses output of "Hg version" command.
   */

  @NotNull
  public static HgVersion parseVersion(@Nullable String output) throws ParseException {
    if (StringUtil.isEmptyOrSpaces(output)) {
      throw new ParseException("Empty hg version output: " + output, 0);
    }
    Matcher matcher = HG_VERSION_PATTERN.matcher(output);
    if (matcher.matches()) {
      return new HgVersion(getIntGroup(matcher, 1), getIntGroup(matcher, 2), getIntGroup(matcher, 3));
    }
    LOGGER.error("Couldn't identify hg version: " + output);
    throw new ParseException("Unsupported format of hg version output: " + output, 0);
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
    String hgExecutable = executable.trim();
    ShellCommand shellCommand = new ShellCommand(false);
    List<String> cmdArgs = new ArrayList<String>();
    cmdArgs.add(hgExecutable);
    cmdArgs.add("version");
    cmdArgs.add("-q");
    HgCommandResult versionResult = shellCommand
      .execute(cmdArgs, null, Charset.defaultCharset());
    return parseVersion(versionResult.getRawOutput());
  }

  /**
   * @return true if the version is supported by the plugin
   */
  public boolean isSupported() {
    return !isNull() && compareTo(MIN) >= 0;
  }

  public boolean isAmendSupported() {
    return !isNull() && compareTo(AMENDSUPPORTED) >= 0;
  }

  /**
   * Note: this class has a natural ordering that is inconsistent with equals.
   * Two HgVersions are equal if their number versions are equal and if their types are equal.
   * Types are considered equal also if one of them is undefined. Otherwise they are compared.
   */
  @Override
  public boolean equals(final Object obj) {
    if (!(obj instanceof HgVersion)) {
      return false;
    }
    HgVersion other = (HgVersion)obj;
    if (compareTo(other) != 0) {
      return false;
    }
    return true;
  }

  /**
   * Hashcode is computed from numbered components of the version. Thus HgVersions with the same numbered components will be compared
   * by equals, and there the type will be taken into consideration).
   */
  @Override
  public int hashCode() {
    return myHashCode;
  }

  /**
   * Note: this class has a natural ordering that is inconsistent with equals.
   * Only numbered versions are compared, so
   * (Hg 1.7.3).compareTo(1.7.3) == 0
   * <p/>
   * {@link HgVersion#NULL} is less than any other not-NULL version.
   */
  public int compareTo(@NotNull HgVersion o) {
    if (o.isNull()) {
      return isNull() ? 0 : 1;
    }
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

  public boolean isNull() {
    return myMajor == 0 && myMiddle == 0 && myMinor == 0;
  }
}
