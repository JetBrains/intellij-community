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
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.util.HgChangesetUtil;
import org.zmlx.hg4idea.util.HgUtil;
import org.zmlx.hg4idea.util.HgVersion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Parse one log command line and create appropriate type of commit info or revision info.
 * see {@link HgHistoryUtil}
 *
 * @param <CommitT>
 */
public abstract class HgBaseLogParser<CommitT> implements Function<String, CommitT> {
  private static final Logger LOG = Logger.getInstance(HgBaseLogParser.class);

  private static final int REVISION_INDEX = 0;
  private static final int CHANGESET_INDEX = 1;
  private static final int PARENTS_INDEX = 2;
  private static final int DATE_INDEX = 3;
  private static final int AUTHOR_INDEX = 4;

  protected static final int MESSAGE_INDEX = 5;
  protected static final int BRANCH_INDEX = 6;

  protected static final int FILES_ADDED_INDEX = 7;
  protected static final int FILES_MODIFIED_INDEX = 8;
  protected static final int FILES_DELETED_INDEX = 9;
  protected static final int FILES_COPIED_INDEX = 10;

  @Nullable
  public CommitT convert(@NotNull String line) {

    // we need to get all attributes, include empty trailing strings, so use non-positive limit as second argument
    List<String> attributes = StringUtil.split(line, HgChangesetUtil.ITEM_SEPARATOR, true, false);
    int numAttributes = attributes.size();
    if (numAttributes <= AUTHOR_INDEX) {
      LOG.info("Hg Log Command was cancelled or failed");
      return null;
    }
    try {
      String revisionString = attributes.get(REVISION_INDEX);
      String changeset = attributes.get(CHANGESET_INDEX);
      String parentsString = attributes.get(PARENTS_INDEX);

      SmartList<HgRevisionNumber> parents = parseParentRevisions(parentsString, revisionString);
      String unixTimeStamp = ContainerUtil.getFirstItem(StringUtil.split(attributes.get(DATE_INDEX), " "));
      if (unixTimeStamp == null) {
        LOG.warn("Error parsing date in line " + line);
        return null;
      }
      Date revisionDate = new Date(Long.parseLong(unixTimeStamp.trim()) * 1000);
      Couple<String> authorAndEmail = HgUtil.parseUserNameAndEmail(attributes.get(AUTHOR_INDEX));
      return convertDetails(revisionString, changeset, parents, revisionDate, authorAndEmail.first, authorAndEmail.second, attributes);
    }
    catch (NumberFormatException e) {
      LOG.warn("Error parsing rev in line " + line);
      return null;
    }
  }

  @Override
  public CommitT fun(String s) {
    return convert(s);
  }

  @Nullable
  protected abstract CommitT convertDetails(@NotNull String rev,
                                            @NotNull String changeset,
                                            @NotNull SmartList<HgRevisionNumber> parents,
                                            @NotNull Date revisionDate,
                                            @NotNull String author,
                                            @NotNull String email,
                                            @NotNull List<String> attributes);

  @NotNull
  public static List<String> constructDefaultTemplate(HgVersion currentVersion) {
    List<String> templates = new ArrayList<>();
    templates.add("{rev}");
    templates.add("{node}");
    if (currentVersion.isParentRevisionTemplateSupported()) {
      templates.add("{p1rev}:{p1node} {p2rev}:{p2node}");
    }
    else {
      templates.add("{parents}");
    }
    templates.addAll(Arrays.asList("{date|hgdate}", "{author}"));
    return templates;
  }

  @NotNull
  public static String[] constructFullTemplateArgument(boolean includeFiles, @NotNull HgVersion currentVersion) {
    List<String> templates = new ArrayList<>();
    templates.add("{rev}");
    templates.add("{node}");
    if (currentVersion.isParentRevisionTemplateSupported()) {
      templates.add("{p1rev}:{p1node} {p2rev}:{p2node}");
    }
    else {
      templates.add("{parents}");
    }
    templates.addAll(Arrays.asList("{date|hgdate}", "{author}", "{desc}", "{branch}"));
    if (!includeFiles) {
      return ArrayUtil.toStringArray(templates);
    }
    List<String> fileTemplates = ContainerUtil.newArrayList("file_adds", "file_mods", "file_dels", "file_copies");
    templates.addAll(wrapIn(fileTemplates, currentVersion));
    return ArrayUtil.toStringArray(templates);
  }

  @NotNull
  private static List<String> wrapIn(@NotNull List<String> fileTemplates, @NotNull HgVersion currentVersion) {
    final boolean supported = currentVersion.isBuiltInFunctionSupported();
    return ContainerUtil.map(fileTemplates, s -> supported ? "{join(" + s + ",'" + HgChangesetUtil.FILE_SEPARATOR + "')}" : "{" + s + "}");
  }

  @NotNull
  protected static SmartList<HgRevisionNumber> parseParentRevisions(@NotNull String parentsString, @NotNull String currentRevisionString) {
    SmartList<HgRevisionNumber> parents = new SmartList<>();
    if (StringUtil.isEmptyOrSpaces(parentsString)) {
      // parents shouldn't be empty  only if not supported
      Long revision = Long.valueOf(currentRevisionString);
      HgRevisionNumber parentRevision = HgRevisionNumber.getLocalInstance(String.valueOf(revision - 1));
      parents.add(parentRevision);
      return parents;
    }
    //hg returns parents in the format 'rev:node rev:node ' (note the trailing space)
    List<String> parentStrings = StringUtil.split(parentsString.trim(), " ");
    for (String parentString : parentStrings) {
      List<String> parentParts = StringUtil.split(parentString, ":");
      //if revision has only 1 parent and "--debug" argument were added or if appropriate parent template were used,
      // its second parent has revision number  -1
      if (Integer.valueOf(parentParts.get(0)) >= 0) {
        parents.add(HgRevisionNumber.getInstance(parentParts.get(0), parentParts.get(1)));
      }
    }
    return parents;
  }

  @NotNull
  protected static String parseAdditionalStringAttribute(final List<String> attributes, int index) {
    int numAttributes = attributes.size();
    if (numAttributes > index) {
      return attributes.get(index);
    }
    LOG.warn("Couldn't parse hg log commit info attribute " + index);
    return "";
  }

  @NotNull
  public static String extractSubject(@NotNull String message) {
    int subjectIndex = message.indexOf('\n');
    return subjectIndex == -1 ? message : message.substring(0, subjectIndex);
  }
}

