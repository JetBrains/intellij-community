// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.command;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang.StringUtils;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgFileRevision;
import org.zmlx.hg4idea.HgRevisionNumber;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class HgRevisionsCommand {

  private static final Logger LOG = Logger.getInstance(HgRevisionsCommand.class.getName());

  private static final String TEMPLATE = "{rev}|"
    + "{node|short}|"
    + "{date|isodate}|"
    + "{author}|"
    + "{branches}|"
    + "{desc}|"
    + "{file_adds}|"
    + "{file_mods}|"
    + "{file_dels}|"
    + "{file_copies}\\n";

  private static final int REVISION_INDEX = 0;
  private static final int CHANGESET_INDEX = 1;
  private static final int DATE_INDEX = 2;
  private static final int AUTHOR_INDEX = 3;
  private static final int BRANCH_INDEX = 4;
  private static final int MESSAGE_INDEX = 5;
  private static final int FILES_ADDED_INDEX = 6;
  private static final int FILES_MODIFIED_INDEX = 7;
  private static final int FILES_DELETED_INDEX = 8;
  private static final int FILES_COPIES_INDEX = 9;
  private static final int ITEM_COUNT = 10;

  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm Z");

  private final Project project;

  public HgRevisionsCommand(Project project) {
    this.project = project;
  }

  protected abstract HgCommandResult execute(
    HgCommandService service, VirtualFile repo, String template, int limit, HgFile hgFile
  );

  public final List<HgFileRevision> execute(HgFile hgFile, int limit) {
    if (limit <= REVISION_INDEX || hgFile == null || hgFile.getRepo() == null) {
      return Collections.emptyList();
    }

    HgCommandService hgCommandService = HgCommandService.getInstance(project);

    HgCommandResult result = execute(
      hgCommandService, hgFile.getRepo(), TEMPLATE, limit, hgFile
    );

    List<HgFileRevision> revisions = new LinkedList<HgFileRevision>();
    for (String line : result.getOutputLines()) {
      try {
        String[] attributes = StringUtils.splitPreserveAllTokens(line, '|');
        if (attributes.length != ITEM_COUNT) {
          LOG.debug("Wrong format. Skipping line " + line);
          continue;
        }
        HgRevisionNumber vcsRevisionNumber = HgRevisionNumber.getInstance(
          attributes[REVISION_INDEX],
          attributes[CHANGESET_INDEX]
        );
        Date revisionDate = DATE_FORMAT.parse(attributes[DATE_INDEX]);
        String author = attributes[AUTHOR_INDEX];
        String branchName = attributes[BRANCH_INDEX];
        String commitMessage = attributes[MESSAGE_INDEX];
        Set<String> filesAdded = parseFileList(attributes[FILES_ADDED_INDEX]);
        Set<String> filesModified = parseFileList(attributes[FILES_MODIFIED_INDEX]);
        Set<String> filesDeleted = parseFileList(attributes[FILES_DELETED_INDEX]);
        Map<String, String> filesCopied = parseCopiesFileList(attributes[FILES_COPIES_INDEX]);

        filesModified.removeAll(filesAdded);
        filesModified.removeAll(filesDeleted);
        filesCopied.keySet().removeAll(filesDeleted);
        filesCopied.values().removeAll(filesAdded);

        if (filesAdded.isEmpty() && filesModified.isEmpty()
          && filesCopied.isEmpty() && filesDeleted.isEmpty()) {
          continue;
        }

        HgFileRevision revision = new HgFileRevision(project, hgFile, vcsRevisionNumber);
        revision.setBranchName(branchName);
        revision.setRevisionDate(revisionDate);
        revision.setAuthor(author);
        revision.setCommitMessage(commitMessage);
        revision.setFilesModified(filesModified);
        revision.setFilesAdded(filesAdded);
        revision.setFilesDeleted(filesDeleted);
        revision.setFilesCopied(filesCopied);

        revisions.add(revision);

      } catch (NumberFormatException e) {
        LOG.warn("Error parsing rev in line " + line);
      } catch (ParseException e) {
        LOG.warn("Error parsing date in line " + line);
      }
    }
    return revisions;
  }

  private Set<String> parseFileList(String fileListString) {
    if (StringUtils.isEmpty(fileListString)) {
      return Collections.emptySet();
    } else {
      return new HashSet<String>(Arrays.asList(fileListString.split(" ")));
    }
  }

  private Map<String, String> parseCopiesFileList(String fileListString) {
    if (StringUtils.isEmpty(fileListString)) {
      return Collections.emptyMap();
    } else {
      Map<String, String> copies = new HashMap<String, String>();
      //{file_copies} gives us a string of this shape:
      // path1/File1 (path2/File2)path3/File3 path4/File4
      // where source (copy)
      String[] filecopies = StringUtils.split(fileListString, ')');
      for (String filecopy : filecopies) {
        String[] parts = StringUtils.split(filecopy, " (");
        if (parts.length != 2) {
          continue;
        }
        copies.put(parts[1], parts[0]);
      }
      return copies;
    }
  }

}
