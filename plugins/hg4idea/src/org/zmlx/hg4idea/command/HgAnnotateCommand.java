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
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.provider.annotate.HgAnnotationLine;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HgAnnotateCommand {

  private static final Logger LOG = Logger.getInstance(HgAnnotateCommand.class);
  private static final Pattern LINE_PATTERN = Pattern.compile(
    "\\s*(.+)\\s+([0-9]+)\\s+([0-9a-fA-F]+)\\s+([a-zA-Z]{3}\\s+[a-zA-Z]{3}\\s+[0-9]{2}\\s+.*[0-9]{4}.+):\\s*([0-9]+):\\s(.*)"
  );

  private static final int USER_GROUP = 1;
  private static final int REVISION_GROUP = 2;
  private static final int CHANGESET_GROUP = 3;
  private static final int DATE_GROUP = 4;
  private static final int LINE_NUMBER_GROUP = 5;
  private static final int CONTENT_GROUP = 6;

  private final Project myProject;

  public HgAnnotateCommand(Project project) {
    myProject = project;
  }

  public List<HgAnnotationLine> execute(@NotNull HgFile hgFile, @Nullable HgRevisionNumber revision) {
    final List<String> arguments = new ArrayList<>();
    arguments.add("-cvnudl");
    HgVcs vcs = HgVcs.getInstance(myProject);
    if (vcs != null &&
        vcs.getProjectSettings().isWhitespacesIgnoredInAnnotations() &&
        vcs.getVersion().isIgnoreWhitespaceDiffInAnnotationsSupported()) {
      arguments.add("-w");
    }
    if (revision != null) {
      arguments.add("-r");
      arguments.add(revision.getChangeset());
    }
    arguments.add(hgFile.getRelativePath());
    final HgCommandResult result = new HgCommandExecutor(myProject).executeInCurrentThread(hgFile.getRepo(), "annotate", arguments);

    if (result == null) {
      return Collections.emptyList();
    }

    List<String> outputLines = result.getOutputLines();
    return parse(outputLines);
  }

  private static List<HgAnnotationLine> parse(List<String> outputLines) {
    List<HgAnnotationLine> annotations = new ArrayList<>(outputLines.size());
    for (String line : outputLines) {
      Matcher matcher = LINE_PATTERN.matcher(line);
      if (matcher.matches()) {
        String user = matcher.group(USER_GROUP).trim();
        HgRevisionNumber rev = HgRevisionNumber.getInstance(matcher.group(REVISION_GROUP), matcher.group(CHANGESET_GROUP));
        String dateGroup = matcher.group(DATE_GROUP).trim();
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", Locale.US);
        String date = "";
        try {
          date = DateFormatUtil.formatPrettyDate(dateFormat.parse(dateGroup));
        }
        catch (ParseException e) {
          LOG.error("Couldn't parse annotation date ", e);
        }
        Integer lineNumber = Integer.valueOf(matcher.group(LINE_NUMBER_GROUP));
        String content = matcher.group(CONTENT_GROUP);
        HgAnnotationLine annotationLine = new HgAnnotationLine(
          user, rev, date, lineNumber, content
        );
        annotations.add(annotationLine);
      }
    }
    return annotations;
  }

}
