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

import com.intellij.openapi.project.Project;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.provider.annotate.HgAnnotationLine;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HgAnnotateCommand {

  private static final Pattern LINE_PATTERN = Pattern.compile(
    "(.+)\\s+([0-9]+)\\s+([0-9a-f]+)\\s+([0-9]{4}-[0-9]{2}-[0-9]{2}):([0-9]+):\\s(.*)"
  );

  private static final int USER_GROUP = 1;
  private static final int REVISION_GROUP = 2;
  private static final int CHANGESET_GROUP = 3;
  private static final int DATE_GROUP = 4;
  private static final int LINE_NUMBER_GROUP = 5;
  private static final int CONTENT_GROUP = 6;

  private final Project project;

  public HgAnnotateCommand(Project project) {
    this.project = project;
  }

  public List<HgAnnotationLine> execute(@NotNull HgFile hgFile) {
    HgCommandService service = HgCommandService.getInstance(project);
    HgCommandResult result = service.execute(
      hgFile.getRepo(), "annotate", Arrays.asList("-cqnudl", hgFile.getRelativePath())
    );

    List<HgAnnotationLine> annotations = new ArrayList<HgAnnotationLine>();
    for (String line : result.getOutputLines()) {
      Matcher matcher = LINE_PATTERN.matcher(line);
      if (matcher.matches()) {
        String user = matcher.group(USER_GROUP);
        HgRevisionNumber revision = HgRevisionNumber.getInstance(
          matcher.group(REVISION_GROUP),
          matcher.group(CHANGESET_GROUP)
        );
        String date = matcher.group(DATE_GROUP);
        Integer lineNumber = Integer.valueOf(matcher.group(LINE_NUMBER_GROUP));
        String content = matcher.group(CONTENT_GROUP);
        HgAnnotationLine annotationLine = new HgAnnotationLine(
          user, revision, date, lineNumber, content
        );
        annotations.add(annotationLine);
      }
    }
    return annotations;
  }

}
