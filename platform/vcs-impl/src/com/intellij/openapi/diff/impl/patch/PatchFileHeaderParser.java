/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.patch;

import com.intellij.util.ObjectUtils;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.impl.VcsUserImpl;
import org.jetbrains.annotations.NonNls;

import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.diff.impl.patch.PatchReader.HASH_PATTERN;

public class PatchFileHeaderParser {

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
