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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsRefType;
import com.intellij.vcs.log.impl.VcsRefImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgChangesetUtil;
import org.zmlx.hg4idea.util.HgUtil;
import org.zmlx.hg4idea.util.HgVersion;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

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
  //if you need full changes, you needn't refs; on the contrary is also true
  private static final int BOOKMARKS_INDEX = 7;
  private static final int TAGS_INDEX = 8;

  protected static final int FILES_ADDED_INDEX = 7;
  protected static final int FILES_MODIFIED_INDEX = 8;
  protected static final int FILES_DELETED_INDEX = 9;
  protected static final int FILES_COPIED_INDEX = 10;

  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");


  @Nullable
  private CommitT convert(@NotNull String line) {

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

      Date revisionDate = DATE_FORMAT.parse(attributes.get(DATE_INDEX));
      Couple<String> authorAndEmail = HgUtil.parseUserNameAndEmail(attributes.get(AUTHOR_INDEX));
      return convertDetails(revisionString, changeset, parents, revisionDate, authorAndEmail.first, authorAndEmail.second, attributes);
    }
    catch (ParseException e) {
      LOG.warn("Error parsing date in line " + line);
      return null;
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
    List<String> templates = new ArrayList<String>();
    templates.add("{rev}");
    templates.add("{node}");
    if (currentVersion.isParentRevisionTemplateSupported()) {
      templates.add("{p1rev}:{p1node} {p2rev}:{p2node}");
    }
    else {
      templates.add("{parents}");
    }
    templates.addAll(Arrays.asList("{date|isodatesec}", "{author}"));
    return templates;
  }

  @NotNull
  public static List<String> constructCommitInfoWithRefTemplate(@NotNull HgVersion currentVersion) {
    List<String> templates = new ArrayList<String>(constructDefaultTemplate(currentVersion));
    templates.add("{desc}");
    templates.add(currentVersion.isRevsetInTemplatesSupport() ? "{ifcontains(rev, revset('head()'), branch)}" : "{branch}");
    templates.addAll(formatRefLists(Arrays.asList("bookmark", "tag")));
    return templates;
  }

  @NotNull
  private static Collection<String> formatRefLists(@NotNull List<String> names) {
    return ContainerUtil.map(names, new Function<String, String>() {
      @Override
      public String fun(String s) {
        String plural = StringUtil.pluralize(s);
        return "{" + plural + "%'{" + s + "}" + HgChangesetUtil.FILE_SEPARATOR + "'}";
      }
    });
  }

  @NotNull
  public static String[] constructFullCommitTemplateArgument(boolean includeFiles, @NotNull HgVersion currentVersion) {
    List<String> templates = new ArrayList<String>(constructDefaultTemplate(currentVersion));
    templates.addAll(Arrays.asList("{desc}", "{branch}"));
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
    return ContainerUtil.map(fileTemplates, new Function<String, String>() {
      @Override
      public String fun(String s) {
        return supported ? "{join(" + s + ",'" + HgChangesetUtil.FILE_SEPARATOR + "')}" : "{" + s + "}";
      }
    });
  }

  @NotNull
  private static SmartList<HgRevisionNumber> parseParentRevisions(@NotNull String parentsString, @NotNull String currentRevisionString) {
    SmartList<HgRevisionNumber> parents = new SmartList<HgRevisionNumber>();
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
  protected static List<? extends VcsRef> parseRefsFromCommitRecord(@NotNull final Hash hash,
                                                                    @NotNull final HgRepository repository,
                                                                    @NotNull List<String> attributes,
                                                                    @NotNull final Collection<String> localTagNames,
                                                                    @NotNull HgVersion currentVersion) {
    final VirtualFile repositoryRoot = repository.getRoot();
    List<VcsRef> refs = ContainerUtil.newArrayList();
    // we could parse branch references only if revset function supported by hg
    if (currentVersion.isRevsetInTemplatesSupport()) {
      String branchName = parseAdditionalStringAttribute(attributes, BRANCH_INDEX);
      if (!StringUtil.isEmptyOrSpaces(branchName)) {
        refs.add(new VcsRefImpl(hash, branchName,
                                repository.getOpenedBranches().contains(branchName) ? HgRefManager.BRANCH : HgRefManager.CLOSED_BRANCH,
                                repositoryRoot));
      }
    }
    // parse bookmarks
    refs.addAll(ContainerUtil
                  .map(StringUtil.split(parseAdditionalStringAttribute(attributes, BOOKMARKS_INDEX), HgChangesetUtil.FILE_SEPARATOR),
                       new Function<String, VcsRef>() {
                         @Override
                         public VcsRef fun(String s) {
                           return new VcsRefImpl(hash, s, HgRefManager.BOOKMARK, repositoryRoot);
                         }
                       }));
    // parse tags
    refs.addAll(ContainerUtil.map(
      StringUtil.split(parseAdditionalStringAttribute(attributes, TAGS_INDEX), HgChangesetUtil.FILE_SEPARATOR),
      new Function<String, VcsRef>() {
        @Override
        public VcsRef fun(String tagName) {
          VcsRefType refType = tagName.equals(HgUtil.TIP_REFERENCE) ? HgRefManager.TIP : localTagNames.contains(tagName)
                                                                                         ? HgRefManager.LOCAL_TAG
                                                                                         : HgRefManager.TAG;
          return new VcsRefImpl(hash, tagName, refType, repositoryRoot);
        }
      }));
    return refs;
  }
}

