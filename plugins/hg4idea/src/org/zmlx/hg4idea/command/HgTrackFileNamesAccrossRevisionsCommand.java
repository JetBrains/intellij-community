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

import com.intellij.openapi.diagnostic.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.vfs.*;
import org.apache.commons.lang.*;
import org.zmlx.hg4idea.*;

import java.util.*;

class HgTrackFileNamesAccrossRevisionsCommand {

  private static final Logger LOG = Logger.getInstance(HgTrackFileNamesAccrossRevisionsCommand.class.getName());
  private static final String SEPARATOR_STRING = "\u0027"; //ascii: end of transmission block

  private static final String TEMPLATE = "{rev}|{file_dels}|{file_copies}" + SEPARATOR_STRING;
  private static final int ITEM_COUNT = 3;

  private static final int REVISION_INDEX = 0;
  private static final int FILES_DELETED_INDEX = 1;
  private static final int FILES_COPIED_INDEX = 2;

  private final Project project;

  public HgTrackFileNamesAccrossRevisionsCommand(Project project) {
    this.project = project;
  }

  private HgCommandResult execute(HgCommandService service, VirtualFile repo, int limit, HgFile hgFile, String currentrevision, String givenRevision) {
    List<String> arguments = new LinkedList<String>();

    arguments.add("--rev");
    arguments.add(currentrevision + ":" + givenRevision);

    arguments.add("--follow");
    arguments.add("--template");
    arguments.add(TEMPLATE);

    if (limit != -1) {
      arguments.add("--limit");
      arguments.add(String.valueOf(limit));
    }

    arguments.add(hgFile.getRelativePath());

    return service.execute(repo, "log", arguments);
  }

  public final String execute(HgFile hgFile, String currentRevision, String givenRevision, int limit) {
    if (hgFile == null || hgFile.getRepo() == null) {
      return null;
    }

    HgCommandService hgCommandService = HgCommandService.getInstance(project);
    HgCommandResult result = execute(hgCommandService, hgFile.getRepo(), limit, hgFile, currentRevision, givenRevision);

    String output = result.getRawOutput();
    String[] changeSets = output.split(SEPARATOR_STRING);
    String currentFileName = hgFile.getRelativePath();
    // needed on windows machines
    currentFileName = currentFileName.replaceAll("\\\\", "/");

    for (String line : changeSets) {
      try {
        String[] attributes = StringUtils.splitPreserveAllTokens(line, '|');
        if (attributes.length != ITEM_COUNT) {
          LOG.debug("Wrong format. Skipping line " + line);
          continue;
        }

        String revisionString = attributes[REVISION_INDEX];

        if (revisionString.equals(givenRevision)) {
          return currentFileName;
        }

        Set<String> filesDeleted;
        Map<String, String> copies;

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

        if (copies.containsValue(currentFileName)) {
          for (String key : copies.keySet()) {
            if (copies.get(key).equals(currentFileName)) {
              currentFileName = key;
              break;
            }
          }
        }
      } catch (NumberFormatException e) {
        LOG.warn("Error parsing rev in line " + line);
      }
    }

    return null;
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
