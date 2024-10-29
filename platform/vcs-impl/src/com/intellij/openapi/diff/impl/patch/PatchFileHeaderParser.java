// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch;

import com.intellij.util.ObjectUtils;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.impl.VcsUserImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;

import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.diff.impl.patch.PatchReader.HASH_PATTERN;

@ApiStatus.Internal
public final class PatchFileHeaderParser {

  @NonNls private static final Pattern ourBaseRevisionPattern = Pattern.compile("From\\s+(" + HASH_PATTERN + ")\\s+.*");
  @NonNls private static final Pattern ourAuthorPattern = Pattern.compile("From:\\s+(.*?)\\s*(?:<(.*)>\\s*)?");

  @NonNls private static final Pattern ourSubjectPattern = Pattern.compile("Subject:(?:\\s+\\[PATCH.*])?\\s*(.+)");
  @NonNls private static final Pattern ourHeaderEndMarker = Pattern.compile("---\\s*");

  public static PatchFileHeaderInfo parseHeader(Iterator<String> iterator) {
    String lineSeparator = "\n";
    StringBuilder message = new StringBuilder();
    VcsUser author = null;
    String revision = null;
    boolean treatAllAsMessageLine = false;

    while (iterator.hasNext()) {
      String curLine = iterator.next();
      Matcher revisionMatcher = ourBaseRevisionPattern.matcher(curLine);
      Matcher authorMatcher = ourAuthorPattern.matcher(curLine);
      Matcher subjectMatcher = ourSubjectPattern.matcher(curLine);
      Matcher endHeaderMatcher = ourHeaderEndMarker.matcher(curLine);
      if (endHeaderMatcher.matches()) {
        break;
      }
      else if (treatAllAsMessageLine) {
        message.append(lineSeparator).append(curLine);
      }
      else if (revisionMatcher.matches()) {
        revision = revisionMatcher.group(1);
      }
      else if (authorMatcher.matches()) {
        author = new VcsUserImpl(authorMatcher.group(1), ObjectUtils.chooseNotNull(authorMatcher.group(2), ""));
      }
      else if (subjectMatcher.matches()) {
        message.append(subjectMatcher.group(1));
        treatAllAsMessageLine = true;
      }
    }

    return new PatchFileHeaderInfo(message.toString().trim(), author, revision);
  }
}
