/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 24.11.2006
 * Time: 15:29:45
 */
package com.intellij.openapi.diff.impl.patch;

import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.io.Writer;
import java.text.MessageFormat;
import java.util.Collection;

public class UnifiedDiffWriter {
  @NonNls private static final String INDEX_SIGNATURE = "Index: {0}{1}";
  private static final String HEADER_SEPARATOR = "===================================================================";

  private UnifiedDiffWriter() {
  }

  public static void write(Collection<FilePatch> patches, Writer writer, final String lineSeparator) throws IOException {
    for(FilePatch filePatch: patches) {
      if (!(filePatch instanceof TextFilePatch)) continue;
      TextFilePatch patch = (TextFilePatch) filePatch;
      writeFileHeading(patch, writer, lineSeparator);
      for(PatchHunk hunk: patch.getHunks()) {
        writeHunkStart(writer, hunk.getStartLineBefore(), hunk.getEndLineBefore(), hunk.getStartLineAfter(), hunk.getEndLineAfter(),
                       lineSeparator);
        for(PatchLine line: hunk.getLines()) {
          char prefixChar = ' ';
          switch(line.getType()) {
            case ADD: prefixChar = '+'; break;
            case REMOVE: prefixChar = '-'; break;
            case CONTEXT: prefixChar = ' '; break;
          }
          String text = line.getText();
          if (text.endsWith("\n")) {
            text = text.substring(0, text.length()-1);
          }
          writeLine(writer, text, prefixChar);
          if (line.isSuppressNewLine()) {
            writer.write(lineSeparator + PatchReader.NO_NEWLINE_SIGNATURE + lineSeparator);
          }
          else {
            writer.write(lineSeparator);
          }
        }
      }
    }
  }

  private static void writeFileHeading(final FilePatch patch, final Writer writer, final String lineSeparator) throws IOException {
    writer.write(MessageFormat.format(INDEX_SIGNATURE, patch.getBeforeName(), lineSeparator));
    writer.write(HEADER_SEPARATOR + lineSeparator);
    writeRevisionHeading(writer, "---", patch.getBeforeName(), patch.getBeforeVersionId(), lineSeparator);
    writeRevisionHeading(writer, "+++", patch.getAfterName(), patch.getAfterVersionId(), lineSeparator);
  }

  private static void writeRevisionHeading(final Writer writer, final String prefix,
                                           final String revisionPath, final String revisionName,
                                           final String lineSeparator)
    throws IOException {
    writer.write(prefix + " ");
    writer.write(revisionPath);
    writer.write("\t");
    writer.write(revisionName);
    writer.write(lineSeparator);
  }

  private static void writeHunkStart(Writer writer, int startLine1, int endLine1, int startLine2, int endLine2,
                                     final String lineSeparator)
    throws IOException {
    StringBuilder builder = new StringBuilder("@@ -");
    builder.append(startLine1+1).append(",").append(endLine1-startLine1);
    builder.append(" +").append(startLine2+1).append(",").append(endLine2-startLine2).append(" @@").append(lineSeparator);
    writer.append(builder.toString());
  }

  private static void writeLine(final Writer writer, final String line, final char prefix) throws IOException {
    writer.write(prefix);
    writer.write(line);
  }
}