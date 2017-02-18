/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.zmlx.hg4idea.log;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgFileRevision;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.util.HgChangesetUtil;
import org.zmlx.hg4idea.util.HgVersion;

import java.util.*;

public class HgFileRevisionLogParser extends HgBaseLogParser<HgFileRevision> {
  private static final Logger LOG = Logger.getInstance(HgFileRevisionLogParser.class);
  @NotNull private final HgFile myHgFile;
  @NotNull private final Project myProject;
  @NotNull private HgVersion myVersion;

  public HgFileRevisionLogParser(@NotNull Project project, @NotNull HgFile hgFile, @NotNull HgVersion currentVersion) {
    myProject = project;
    myHgFile = hgFile;
    myVersion = currentVersion;
  }

  @Override
  @Nullable
  protected HgFileRevision convertDetails(@NotNull String rev,
                                          @NotNull String changeset,
                                          @NotNull SmartList<HgRevisionNumber> parents,
                                          @NotNull Date revisionDate,
                                          @NotNull String author,
                                          @NotNull String email,
                                          @NotNull List<String> attributes) {
    int numAttributes = attributes.size();
    String commitMessage = parseAdditionalStringAttribute(attributes, MESSAGE_INDEX);
    String branchName = parseAdditionalStringAttribute(attributes, BRANCH_INDEX);
    final HgRevisionNumber vcsRevisionNumber = new HgRevisionNumber(rev, changeset, author, email, commitMessage, parents);

    Set<String> filesAdded = Collections.emptySet();
    Set<String> filesModified = Collections.emptySet();
    Set<String> filesDeleted = Collections.emptySet();
    Map<String, String> copies = Collections.emptyMap();
    boolean shouldParseOldTemplate = !myVersion.isBuiltInFunctionSupported();
    String separator = shouldParseOldTemplate ? " " : HgChangesetUtil.FILE_SEPARATOR;
    // At least in the case of the long template, it's OK that we don't have everything...for example, if there were no
    //  deleted or copied files, then we won't get any attributes for them...
    if (numAttributes > FILES_ADDED_INDEX) {
      filesAdded = parseFileList(attributes.get(FILES_ADDED_INDEX), separator);

      if (numAttributes > FILES_MODIFIED_INDEX) {
        filesModified = parseFileList(attributes.get(FILES_MODIFIED_INDEX), separator);

        if (numAttributes > FILES_DELETED_INDEX) {
          filesDeleted = parseFileList(attributes.get(FILES_DELETED_INDEX), separator);

          if (numAttributes > FILES_COPIED_INDEX) {
            copies = shouldParseOldTemplate
                     ? parseCopiesFileListAsOldVersion(attributes.get(FILES_COPIED_INDEX))
                     : parseCopiesFileList(attributes.get(FILES_COPIED_INDEX));
            // Only keep renames, i.e. copies where the source file is also deleted.
            Iterator<String> keys = copies.keySet().iterator();
            while (keys.hasNext()) {
              String s = keys.next();
              if (filesAdded.contains(copies.get(s)) && filesDeleted.contains(s)) {
                filesAdded.remove(copies.get(s));
                filesDeleted.remove(s);
              }
              else if (!filesDeleted.contains(s)) {
                keys.remove();
              }
            }
          }
        }
      }
    }
    return new HgFileRevision(myProject, myHgFile, vcsRevisionNumber, branchName, revisionDate, vcsRevisionNumber.getAuthor(), commitMessage,
                              filesModified, filesAdded, filesDeleted, copies);
  }

  private static Set<String> parseFileList(@Nullable String fileListString, @NotNull String separator) {
    return StringUtil.isEmpty(fileListString)
           ? Collections.<String>emptySet()
           : new HashSet<>(StringUtil.split(fileListString, separator));
  }

  @NotNull
  static Map<String, String> parseCopiesFileList(@Nullable String fileListString) {
    if (StringUtil.isEmpty(fileListString)) {
      return Collections.emptyMap();
    }
    Map<String, String> copies = new HashMap<>();
    List<String> filesList = StringUtil.split(fileListString, HgChangesetUtil.FILE_SEPARATOR);

    for (String pairOfFiles : filesList) {
      String[] files = pairOfFiles.split("\\s+\\(");
      if (files.length != 2) {
        LOG.info("Couldn't parse copied files: " + fileListString);
        return copies;
      }
      copies.put(files[1].substring(0, files[1].length() - 1), files[0]);
    }
    return copies;
  }

  @NotNull
  static Map<String, String> parseCopiesFileListAsOldVersion(@Nullable String fileListString) {
    if (StringUtil.isEmpty(fileListString)) {
      return Collections.emptyMap();
    }
    else {
      Map<String, String> copies = new HashMap<>();
      //hg copied files output looks like: "target1 (source1)target2 (source2)target3 ....  (target_n)"
      //so we should split i-1 source from i target.
      // If some sources or targets contains '(' we suppose that it has Regular Bracket sequence and perform appropriate string parsing.
      //if it fails just return. (to avoid  ArrayIndexOutOfBoundsException)
      String[] filesList = fileListString.split("\\s+\\(");
      String target = filesList[0];

      for (int i = 1; i < filesList.length; ++i) {
        String source = filesList[i];
        int afterRightBraceIndex = findRightBracePosition(source);
        if (afterRightBraceIndex == -1) {
          break;
        }
        copies.put(source.substring(0, afterRightBraceIndex - 1), target);
        if (afterRightBraceIndex >= source.length()) {                  //the last 'word' in str
          break;
        }
        target = source.substring(afterRightBraceIndex);
      }
      return copies;
    }
  }

  private static int findRightBracePosition(@NotNull String str) {
    int len = str.length();
    int depth = 1;
    for (int i = 0; i < len; ++i) {
      char c = str.charAt(i);
      switch (c) {
        case '(':
          depth++;
          break;
        case ')':
          depth--;
          break;
      }
      if (depth == 0) {
        return i + 1;
      }
    }
    LOG.info("Unexpected output during parse copied files in log command " + str);
    return -1;
  }
}
