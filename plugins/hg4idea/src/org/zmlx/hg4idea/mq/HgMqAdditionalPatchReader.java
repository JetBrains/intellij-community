/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.zmlx.hg4idea.mq;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.File;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.List;

public class HgMqAdditionalPatchReader {

  private static final String NODE_ID = "# Node ID ";
  private static final String PARENT_ID = "# Parent ";
  private static final String DATE = "# Date ";
  private static final String BRANCH = "# Branch ";
  private static final String USER = "# User ";
  private static final String DIFF_INFO = "diff -";

  @NotNull
  public static MqPatchDetails readMqPatchInfo(@Nullable VirtualFile root, @Nullable File patchFile) {
    if (patchFile == null) return MqPatchDetails.EMPTY_PATCH_DETAILS;
    String context = DvcsUtil.tryLoadFileOrReturn(patchFile, "");
    return parseAdditionalMqInfo(root == null ? HgUtil.getNearestHgRoot(VfsUtil.findFileByIoFile(patchFile, true)) : root, context);
  }

  @NotNull
  private static MqPatchDetails parseAdditionalMqInfo(@Nullable VirtualFile root, @NotNull String context) {
    String[] lines = StringUtil.splitByLines(context, false);
    String user = null;
    Date date = null;
    String branch = null;
    List<String> messageLines = ContainerUtil.newArrayList();
    String nodeId = null;
    String parent = null;

    for (String line : lines) {
      if (line.startsWith(DIFF_INFO)) {
        break;
      }
      else if (line.startsWith(NODE_ID)) {
        nodeId = extractField(line, NODE_ID);
      }
      else if (line.startsWith(PARENT_ID)) {
        parent = extractField(line, PARENT_ID);
      }
      else if (line.startsWith(DATE)) {
        date = tryParseDate(extractField(line, DATE));
      }
      else if (line.startsWith(BRANCH)) {
        branch = extractField(line, BRANCH);
      }
      else if (line.startsWith(USER)) {
        user = extractField(line, USER);
      }
      else if (!line.startsWith("# ")) {
        messageLines.add(line);
      }
    }
    return new MqPatchDetails(nodeId, parent, date, root, branch, StringUtil.join(messageLines, "\n"), user);
  }

  @NotNull
  private static String extractField(@NotNull String line, @NotNull String prefix) {
    return line.substring(prefix.length()).trim();
  }

  @Nullable
  private static Date tryParseDate(@NotNull String substring) {
    try {
      return new Date(NumberFormat.getInstance().parse(substring).longValue() * 1000);
    }
    catch (ParseException e) {
      return null;
    }
  }

  @SuppressWarnings("unused")
  // for future custom additional patch reader implementation
  public boolean isMqPatch(@NotNull File file) {
    return DvcsUtil.tryLoadFileOrReturn(file, "").startsWith("# HG changeset patch");
  }
}
