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
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgFileRevision;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.util.HgUtil;
import org.zmlx.hg4idea.util.HgChangesetUtil;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.HgCommandExecutor;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

abstract class HgRevisionsCommand {

  private static final Logger LOG = Logger.getInstance(HgRevisionsCommand.class.getName());

  private static final String[] SHORT_TEMPLATE_ITEMS = { "{rev}","{node|short}", "{parents}", "{date|isodatesec}", "{author}", "{branches}", "{desc}" };
  private static final String[] LONG_TEMPLATE_ITEMS =
    { "{rev}", "{node|short}", "{parents}", "{date|isodatesec}", "{author}", "{branches}", "{desc}", "{file_adds}", "{file_mods}", "{file_dels}", "{file_copies}" };

  private static final int REVISION_INDEX = 0;
  private static final int CHANGESET_INDEX = 1;
  private static final int PARENTS_INDEX = 2;

  private static final int DATE_INDEX = 3;
  private static final int AUTHOR_INDEX = 4;
  private static final int BRANCH_INDEX = 5;
  private static final int MESSAGE_INDEX = 6;
  private static final int FILES_ADDED_INDEX = 7;
  private static final int FILES_MODIFIED_INDEX = 8;
  private static final int FILES_DELETED_INDEX = 9;
  private static final int FILES_COPIED_INDEX = 10;

  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");

  private final Project project;

  public HgRevisionsCommand(Project project) {
    this.project = project;
  }

  @Nullable
  protected abstract HgCommandResult execute(
    HgCommandExecutor executor, VirtualFile repo, String template, int limit, HgFile hgFile
  );

  public final List<HgFileRevision> execute(HgFile hgFile, int limit, boolean includeFiles) {
    if ((limit <= 0 && limit != -1) || hgFile == null || hgFile.getRepo() == null) {
      return Collections.emptyList();
    }

    HgCommandExecutor hgCommandExecutor = new HgCommandExecutor(project);

    String template = HgChangesetUtil.makeTemplate(includeFiles ? LONG_TEMPLATE_ITEMS : SHORT_TEMPLATE_ITEMS);
    int itemCount = includeFiles ? LONG_TEMPLATE_ITEMS.length : SHORT_TEMPLATE_ITEMS.length;

    FilePath originalFileName = HgUtil.getOriginalFileName(hgFile.toFilePath(), ChangeListManager.getInstance(project));
    HgFile originalHgFile = new HgFile(hgFile.getRepo(), originalFileName);
    HgCommandResult result = execute(
      hgCommandExecutor, hgFile.getRepo(), template, limit, originalHgFile
    );

    final List<HgFileRevision> revisions = new LinkedList<HgFileRevision>();
    if (result == null) {
      return revisions;
    }

    String output = result.getRawOutput();
    String[] changeSets = output.split(HgChangesetUtil.CHANGESET_SEPARATOR);
    for (String line : changeSets) {
      try {
        String[] attributes = line.split(HgChangesetUtil.ITEM_SEPARATOR);
        if (attributes.length != itemCount) {
          LOG.debug("Wrong format. Skipping line " + line);
          continue;
        }

        String revisionString = attributes[REVISION_INDEX];
        String changeset = attributes[CHANGESET_INDEX];
        String parentsString = attributes[PARENTS_INDEX];

        List<HgRevisionNumber> parents = new ArrayList<HgRevisionNumber>(2);
        if (StringUtils.isEmpty(parentsString)) {
          Long revision = Long.valueOf(revisionString);
          HgRevisionNumber parentRevision = HgRevisionNumber.getLocalInstance(String.valueOf(revision - 1));
          parents.add(parentRevision);
        } else {
          //hg returns parents in the format 'rev:node rev:node ' (note the trailing space)
          String[] parentStrings = parentsString.trim().split(" ");
          for (String parentString : parentStrings) {
            String[] parentParts = parentString.split(":");
            parents.add(HgRevisionNumber.getInstance(parentParts[0], parentParts[1]));
          }
        }
        HgRevisionNumber vcsRevisionNumber = HgRevisionNumber.getInstance(
          revisionString,
          changeset,
          parents
        );

        Date revisionDate = DATE_FORMAT.parse(attributes[DATE_INDEX]);
        String author = attributes[AUTHOR_INDEX];
        String branchName = attributes[BRANCH_INDEX];
        String commitMessage = attributes[MESSAGE_INDEX];

        Set<String> filesAdded;
        Set<String> filesModified;
        Set<String> filesDeleted;
        Map<String, String> copies;
        if (FILES_ADDED_INDEX < itemCount) {
          filesAdded = parseFileList(attributes[FILES_ADDED_INDEX]);
          filesModified = parseFileList(attributes[FILES_MODIFIED_INDEX]);
          filesDeleted = parseFileList(attributes[FILES_DELETED_INDEX]);

          copies = parseCopiesFileList(attributes[FILES_COPIED_INDEX]);
          // Only keep renames, i.e. copies where the source file is also deleted.
          Iterator<String> keys = copies.keySet().iterator();
          while (keys.hasNext()) {
            String s = keys.next();
            if (!filesDeleted.contains(s)) {
              keys.remove();
            }
          }
        } else {
          filesAdded = Collections.emptySet();
          filesModified = Collections.emptySet();
          filesDeleted = Collections.emptySet();
          copies = Collections.emptyMap();
        }

        revisions.add(new HgFileRevision(project, hgFile, vcsRevisionNumber,
          branchName, revisionDate, author, commitMessage, filesModified, filesAdded, filesDeleted, copies));
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

      String[] filesList = fileListString.split("\\)");

      for (String files : filesList) {
        String[] file = files.split(" \\(");
        String target = file[0];
        String source = file[1];

        copies.put(source, target);
      }

      return copies;
    }
  }
}
