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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgFileRevision;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.execution.HgCommandException;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.util.HgChangesetUtil;
import org.zmlx.hg4idea.util.HgUtil;
import org.zmlx.hg4idea.util.HgVersion;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class HgLogCommand {

  private static final Logger LOG = Logger.getInstance(HgLogCommand.class.getName());
  private static final String[] SHORT_TEMPLATE_ITEMS =
    {"{rev}", "{node}", "{parents}", "{date|isodatesec}", "{author}", "{branch}", "{desc}"};
  private static final String[] LONG_TEMPLATE_ITEMS =
    {"{rev}", "{node}", "{parents}", "{date|isodatesec}", "{author}", "{branch}", "{desc}", "{file_adds}",
      "{file_mods}",
      "{file_dels}", "{join(file_copies,'" + HgChangesetUtil.FILE_SEPARATOR + "')}"};
  private static final String[] LONG_TEMPLATE_FOR_OLD_VERSIONS =
    {"{rev}", "{node}", "{parents}", "{date|isodatesec}", "{author}", "{branch}", "{desc}", "{file_adds}",
      "{file_mods}",
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

  @NotNull private final Project myProject;

  private boolean myIncludeRemoved;
  private boolean myFollowCopies;
  private boolean myLogFile = true;
  private boolean myBuiltInSupported = false;
  private boolean myLargeFilesWithFollowSupported = false;

  public void setIncludeRemoved(boolean includeRemoved) {
    myIncludeRemoved = includeRemoved;
  }

  public void setFollowCopies(boolean followCopies) {
    myFollowCopies = followCopies;
  }

  public void setLogFile(boolean logFile) {
    myLogFile = logFile;
  }

  public HgLogCommand(@NotNull Project project) {
    myProject = project;
    HgVcs vcs = HgVcs.getInstance(myProject);
    if (vcs == null) {
      LOG.info("Vcs couldn't be null for project");
      return;
    }
    HgVersion version = vcs.getVersion();
    myBuiltInSupported = version.isBuiltInFunctionSupported();
    myLargeFilesWithFollowSupported = version.isLargeFilesWithFollowSupported();
  }

  /**
   * @param limit Pass -1 to set no limits on history
   */
  public final List<HgFileRevision> execute(final HgFile hgFile, int limit, boolean includeFiles) throws HgCommandException {
    return execute(hgFile, limit, includeFiles, null);
  }

  /**
   * @param limit Pass -1 to set no limits on history
   */
  public final List<HgFileRevision> execute(final HgFile hgFile, int limit, boolean includeFiles, @Nullable List<String> argsForCmd)
    throws HgCommandException {
    if ((limit <= 0 && limit != -1) || hgFile == null || hgFile.getRepo() == null) {
      return Collections.emptyList();
    }

    String template;
    boolean shouldParseOldTemplate = false;

    if (!includeFiles) {
      template = HgChangesetUtil.makeTemplate(SHORT_TEMPLATE_ITEMS);
    }
    else {
      if (!myBuiltInSupported) {
        template = HgChangesetUtil.makeTemplate(LONG_TEMPLATE_FOR_OLD_VERSIONS);
        shouldParseOldTemplate = true;
      }
      else {
        template = HgChangesetUtil.makeTemplate(LONG_TEMPLATE_ITEMS);
      }
    }

    int expectedItemCount = includeFiles ? LONG_TEMPLATE_ITEMS.length : SHORT_TEMPLATE_ITEMS.length;

    FilePath originalFileName = HgUtil.getOriginalFileName(hgFile.toFilePath(), ChangeListManager.getInstance(myProject));
    HgFile originalHgFile = new HgFile(hgFile.getRepo(), originalFileName);
    HgCommandResult result = execute(hgFile.getRepo(), template, limit, originalHgFile, argsForCmd);

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
        //  deleted or copied files, then we won't get any attributes for them...
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

        Date revisionDate = DATE_FORMAT.parse(attributes[DATE_INDEX]);
        String author = attributes[AUTHOR_INDEX];
        String branchName = attributes[BRANCH_INDEX];
        String commitMessage = attributes[MESSAGE_INDEX];

        final HgRevisionNumber vcsRevisionNumber = new HgRevisionNumber(revisionString, changeset, author, commitMessage, parents);

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
                copies = shouldParseOldTemplate
                         ? parseCopiesFileListAsOldVersion(attributes[FILES_COPIED_INDEX])
                         : parseCopiesFileList(attributes[FILES_COPIED_INDEX]);
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
          new HgFileRevision(myProject, hgFile, vcsRevisionNumber, branchName, revisionDate, author, commitMessage,
                             filesModified,
                             filesAdded,
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

  @Nullable
  private HgCommandResult execute(@NotNull VirtualFile repo, @NotNull String template, int limit, HgFile hgFile,
                                  @Nullable List<String> argsForCmd) {
    List<String> arguments = new LinkedList<String>();
    if (myIncludeRemoved) {
      // There is a bug in mercurial that causes --follow --removed <file> to cause
      // an error (http://mercurial.selenic.com/bts/issue2139). Avoid this combination
      // for now, preferring to use --follow over --removed.
      if (!(myFollowCopies && myLogFile)) {
        arguments.add("--removed");
      }
    }
    if (myFollowCopies) {
      arguments.add("--follow");
      //workaround: --follow  options doesn't work with largefiles extension, so we need to switch off this extension in log command
      //see http://selenic.com/pipermail/mercurial-devel/2013-May/051209.html  fixed since 2.7
      if (!myLargeFilesWithFollowSupported) {
        arguments.add("--config");
        arguments.add("extensions.largefiles=!");
      }
    }
    arguments.add("--template");
    arguments.add(template);
    if (limit != -1) {
      arguments.add("--limit");
      arguments.add(String.valueOf(limit));
    }
    if (argsForCmd != null) {
      arguments.addAll(argsForCmd);
    }
    if (myLogFile) {
      arguments.add(hgFile.getRelativePath());
    }
    return new HgCommandExecutor(myProject).executeInCurrentThread(repo, "log", arguments);
  }

  private static Set<String> parseFileList(String fileListString) {
    if (StringUtil.isEmpty(fileListString)) {
      return Collections.emptySet();
    }
    else {
      return new HashSet<String>(Arrays.asList(fileListString.split(" ")));
    }
  }

  @NotNull
  public static Map<String, String> parseCopiesFileList(@Nullable String fileListString) {
    if (StringUtil.isEmpty(fileListString)) {
      return Collections.emptyMap();
    }
    Map<String, String> copies = new HashMap<String, String>();
    String[] filesList = fileListString.split(HgChangesetUtil.FILE_SEPARATOR);

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
  public static Map<String, String> parseCopiesFileListAsOldVersion(@Nullable String fileListString) {
    if (StringUtil.isEmpty(fileListString)) {
      return Collections.emptyMap();
    }
    else {
      Map<String, String> copies = new HashMap<String, String>();
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
