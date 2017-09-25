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
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BinaryPatchContentParser {
  @NonNls private static final Pattern ourGitBinaryLineSize = Pattern.compile("literal\\s+(\\d+)\\s*");

  public static FilePatch readGitBinaryFormatPatch(@NotNull ListIterator<String> iterator, @NotNull FileStatus parsedStatus)
    throws PatchSyntaxException {
    ByteArrayOutputStream afterStream = new ByteArrayOutputStream();
    ByteArrayOutputStream beforeStream = new ByteArrayOutputStream();
    checkNotEOF(iterator);
    try {
      String next = iterator.next();
      Matcher literalMatcher = ourGitBinaryLineSize.matcher(next);
      if (literalMatcher.matches()) {
        getContent(iterator, afterStream, literalMatcher.group(1));
      }
      //parse literal before content if exist
      if (iterator.hasNext() && parsedStatus != FileStatus.ADDED) {
        next = iterator.next();
        if (StringUtil.isEmptyOrSpaces(next) && iterator.hasNext()) {
          next = iterator.next();
        }
        if (literalMatcher.reset(next).matches()) {
          getContent(iterator, beforeStream, literalMatcher.group(1));
        }
        else {
          // does not contain 'before' literal, need to step back;
          iterator.previous();
        }
      }
      return new BinaryFilePatch(parsedStatus == FileStatus.ADDED ? null : beforeStream.toByteArray(),
                                 parsedStatus == FileStatus.DELETED ? null : afterStream.toByteArray());
    }
    catch (Exception e) {
      throw new PatchSyntaxException(iterator.previousIndex(), e.getMessage());
    }
  }

  private static void getContent(@NotNull ListIterator<String> iterator,
                                 @NotNull ByteArrayOutputStream afterStream, @NotNull String lenFromLiteral)
    throws EofBinaryPatchSyntaxException, BinaryEncoder.BinaryPatchException {
    long afterSize = Long.parseLong(lenFromLiteral);
    checkNotEOF(iterator);
    BinaryEncoder.decode(iterator, afterSize, afterStream);
  }

  private static void checkNotEOF(@NotNull ListIterator<String> iterator) throws EofBinaryPatchSyntaxException {
    if (!iterator.hasNext()) throw new EofBinaryPatchSyntaxException(iterator.previousIndex());
  }

  public static class EofBinaryPatchSyntaxException extends PatchSyntaxException {
    public EofBinaryPatchSyntaxException(int line) {
      super(line, "Unexpected end of binary patch");
    }
  }
}
  
