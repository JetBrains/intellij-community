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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgFileRevision;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.execution.HgCommandException;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.util.HgChangesetUtil;
import org.zmlx.hg4idea.util.HgUtil;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

abstract class HgRevisionsCommand {

  private static final Logger LOG = Logger.getInstance(HgRevisionsCommand.class.getName());

  private static final String[] SHORT_TEMPLATE_ITEMS =
    {"{rev}", "{node|short}", "{parents}", "{date|isodatesec}", "{author}", "{branches}", "{desc}"};
  private static final String[] LONG_TEMPLATE_ITEMS =
    {"{rev}", "{node|short}", "{parents}", "{date|isodatesec}", "{author}", "{branches}", "{desc}", "{file_adds}", "{file_mods}",
      "{file_dels}", "{file_copies}"};

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
  protected abstract HgCommandResult execute(HgCommandExecutor executor, VirtualFile repo, String template, int limit, HgFile hgFile);

  protected abstract HgCommandResult execute(HgCommandExecutor executor, VirtualFile repo,
                                             String template, int limit, HgFile hgFile, @Nullable List<String> argsForCmd);

  public final List<HgFileRevision> execute(final HgFile hgFile, int limit, boolean includeFiles) throws HgCommandException {
    return execute(hgFile, limit, includeFiles, null);
  }

  public final List<HgFileRevision> execute(final HgFile hgFile, int limit, boolean includeFiles, @Nullable List<String> argsForCmd)
    throws HgCommandException {
    if ((limit <= 0 && limit != -1) || hgFile == null || hgFile.getRepo() == null) {
      return Collections.emptyList();
    }

    HgCommandExecutor hgCommandExecutor = new HgCommandExecutor(project);

    String template = HgChangesetUtil.makeTemplate(includeFiles ? LONG_TEMPLATE_ITEMS : SHORT_TEMPLATE_ITEMS);
    int expectedItemCount = includeFiles ? LONG_TEMPLATE_ITEMS.length : SHORT_TEMPLATE_ITEMS.length;

    FilePath originalFileName = HgUtil.getOriginalFileName(hgFile.toFilePath(), ChangeListManager.getInstance(project));
    HgFile originalHgFile = new HgFile(hgFile.getRepo(), originalFileName);
    HgCommandResult result;
    if (argsForCmd != null) {
      result = execute(hgCommandExecutor, hgFile.getRepo(), template, limit, originalHgFile, argsForCmd);
    }
    else {
      result = execute(hgCommandExecutor, hgFile.getRepo(), template, limit, originalHgFile);
    }

    final List<HgFileRevision> revisions = new LinkedList<HgFileRevision>();
    if (result == null) {
      return revisions;
    }

    List<String> errors = result.getErrorLines();
    if (errors != null && !errors.isEmpty()) {
      throw new HgCommandException(errors.toString());
    }
    String output = result.getRawOutput();
    String[] changeSets = output.split(HgChangesetUtil.CHANGESET_SEPARATOR);
    for (String line : changeSets) {
      try {
        String[] attributes = line.split(HgChangesetUtil.ITEM_SEPARATOR);
        // At least in the case of the long template, it's OK that we don't have everything...for example, if there were no
        //  deleted or copied files, then we won't get any attribtes for them...
        int numAttributes = attributes.length;
        if (!includeFiles && (numAttributes != expectedItemCount)) {
          LOG.debug("Wrong format. Skipping line " + line);
          continue;
        }
        else if (includeFiles && (numAttributes < FILES_ADDED_INDEX)) {
          LOG.debug("Wrong format for long template. Skipping line " + line);
          continue;
        }

        String revisionString = attributes[REVISION_INDEX];
        String changeset = attributes[CHANGESET_INDEX];
        String parentsString = attributes[PARENTS_INDEX];

        List<HgRevisionNumber> parents = new ArrayList<HgRevisionNumber>(2);
        if (StringUtil.isEmpty(parentsString)) {
          Long revision = Long.valueOf(revisionString);
          HgRevisionNumber parentRevision = HgRevisionNumber.getLocalInstance(String.valueOf(revision - 1));
          parents.add(parentRevision);
        }
        else {
          //hg returns parents in the format 'rev:node rev:node ' (note the trailing space)
          String[] parentStrings = parentsString.trim().split(" ");
          for (String parentString : parentStrings) {
            String[] parentParts = parentString.split(":");
            //if revision has only 1 parent and "--debug" argument were added,  its second parent has revision number  -1
            if (Integer.valueOf(parentParts[0]) >= 0) {
              parents.add(HgRevisionNumber.getInstance(parentParts[0], parentParts[1]));
            }
          }
        }
        final HgRevisionNumber vcsRevisionNumber = HgRevisionNumber.getInstance(revisionString, changeset, parents);

        Date revisionDate = DATE_FORMAT.parse(attributes[DATE_INDEX]);
        String author = attributes[AUTHOR_INDEX];
        String branchName = attributes[BRANCH_INDEX];
        String commitMessage = attributes[MESSAGE_INDEX];

        Set<String> filesAdded = Collections.emptySet();
        Set<String> filesModified = Collections.emptySet();
        Set<String> filesDeleted = Collections.emptySet();
        Map<String, String> copies = Collections.emptyMap();

        if (numAttributes > FILES_ADDED_INDEX) {
          filesAdded = parseFileList(attributes[FILES_ADDED_INDEX]);

          if (numAttributes > FILES_MODIFIED_INDEX) {
            filesModified = parseFileList(attributes[FILES_MODIFIED_INDEX]);

            if (numAttributes > FILES_DELETED_INDEX) {
              filesDeleted = parseFileList(attributes[FILES_DELETED_INDEX]);

              if (numAttributes > FILES_COPIED_INDEX) {
                copies = parseCopiesFileList(attributes[FILES_COPIED_INDEX]);
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

        revisions.add(
          new HgFileRevision(project, hgFile, vcsRevisionNumber, branchName, revisionDate, author, commitMessage, filesModified, filesAdded,
                             filesDeleted, copies));
      }
      catch (NumberFormatException e) {
        LOG.warn("Error parsing rev in line " + line);
      }
      catch (ParseException e) {
        LOG.warn("Error parsing date in line " + line);
      }
    }
    return revisions;
  }

  private Set<String> parseFileList(String fileListString) {
    if (StringUtil.isEmpty(fileListString)) {
      return Collections.emptySet();
    }
    else {
      return new HashSet<String>(Arrays.asList(fileListString.split(" ")));
    }
  }

  private Map<String, String> parseCopiesFileList(String fileListString) {
    if (StringUtil.isEmpty(fileListString)) {
      return Collections.emptyMap();
    }
    else {
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
