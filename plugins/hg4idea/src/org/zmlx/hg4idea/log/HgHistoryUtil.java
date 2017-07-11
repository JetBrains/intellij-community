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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.*;
import org.zmlx.hg4idea.command.HgLogCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.HgLineProcessListener;
import org.zmlx.hg4idea.provider.HgChangeProvider;
import org.zmlx.hg4idea.util.HgChangesetUtil;
import org.zmlx.hg4idea.util.HgUtil;
import org.zmlx.hg4idea.util.HgVersion;

import java.io.File;
import java.util.*;

public class HgHistoryUtil {

  private static final Logger LOG = Logger.getInstance(HgHistoryUtil.class);

  private HgHistoryUtil() {
  }

  @NotNull
  public static List<VcsCommitMetadata> loadMetadata(@NotNull final Project project,
                                                     @NotNull final VirtualFile root, int limit,
                                                     @NotNull List<String> parameters) throws VcsException {

    final VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return Collections.emptyList();
    }
    HgVcs hgvcs = HgVcs.getInstance(project);
    assert hgvcs != null;
    HgVersion version = hgvcs.getVersion();
    List<String> templateList = HgBaseLogParser.constructDefaultTemplate(version);
    templateList.add("{desc}");
    String[] templates = ArrayUtil.toStringArray(templateList);
    HgCommandResult result = getLogResult(project, root, version, limit, parameters, HgChangesetUtil.makeTemplate(templates));
    HgBaseLogParser<VcsCommitMetadata> baseParser = new HgBaseLogParser<VcsCommitMetadata>() {

      @Override
      protected VcsCommitMetadata convertDetails(@NotNull String rev,
                                                 @NotNull String changeset,
                                                 @NotNull SmartList<HgRevisionNumber> parents,
                                                 @NotNull Date revisionDate,
                                                 @NotNull String author,
                                                 @NotNull String email,
                                                 @NotNull List<String> attributes) {
        String message = parseAdditionalStringAttribute(attributes, MESSAGE_INDEX);
        String subject = extractSubject(message);
        List<Hash> parentsHash = new SmartList<>();
        for (HgRevisionNumber parent : parents) {
          parentsHash.add(factory.createHash(parent.getChangeset()));
        }
        return factory.createCommitMetadata(factory.createHash(changeset), parentsHash, revisionDate.getTime(), root,
                                            subject, author, email, message, author, email, revisionDate.getTime());
      }
    };
    return getCommitRecords(project, result, baseParser);
  }

  /**
   * <p>Get & parse hg log detailed output with commits, their parents and their changes.
   * For null destination return log command result</p>
   * <p/>
   * <p>Warning: this is method is efficient by speed, but don't query too much, because the whole log output is retrieved at once,
   * and it can occupy too much memory. The estimate is ~600Kb for 1000 commits.</p>
   */
  @NotNull
  public static List<? extends VcsFullCommitDetails> history(@NotNull Project project, @NotNull VirtualFile root, int limit,
                                                             @NotNull List<String> hashParameters, boolean silent)
    throws VcsException {
    HgVcs hgvcs = HgVcs.getInstance(project);
    assert hgvcs != null;
    HgVersion version = hgvcs.getVersion();
    String[] templates = HgBaseLogParser.constructFullTemplateArgument(true, version);

    ArrayList<VcsFullCommitDetails> result = ContainerUtil.newArrayList();
    VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return Collections.emptyList();
    }

    HgFileRevisionLogParser parser = new HgFileRevisionLogParser(project, getOriginalHgFile(project, root), hgvcs.getVersion());
    try {
      readLog(project, root, hgvcs.getVersion(), limit, hashParameters, HgChangesetUtil.makeTemplate(templates),
              stringBuilder -> {
                HgFileRevision revision = parser.convert(stringBuilder.toString());
                if (revision != null) {
                  result.add(createDetails(project, root, factory, revision));
                }
              });
    }
    catch (VcsException e) {
      if (!silent) {
        VcsNotifier.getInstance(project).notifyError(HgVcsMessages.message("hg4idea.error.log.command.execution"), e.getMessage());
      }
      throw e;
    }
    return result;
  }

  @NotNull
  public static List<? extends VcsFullCommitDetails> createFullCommitsFromResult(@NotNull Project project,
                                                                                 @NotNull VirtualFile root,
                                                                                 @Nullable HgCommandResult result,
                                                                                 @NotNull HgVersion version, boolean silent) {
    final VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return Collections.emptyList();
    }
    List<HgFileRevision> hgRevisions =
      getCommitRecords(project, result, new HgFileRevisionLogParser(project, getOriginalHgFile(project, root), version), silent);
    List<VcsFullCommitDetails> vcsFullCommitDetailsList = new ArrayList<>();
    for (HgFileRevision revision : hgRevisions) {
      vcsFullCommitDetailsList.add(createDetails(project, root, factory, revision));
    }
    return vcsFullCommitDetailsList;
  }

  @NotNull
  public static VcsFullCommitDetails createDetails(@NotNull Project project,
                                                   @NotNull VirtualFile root,
                                                   @NotNull VcsLogObjectsFactory factory,
                                                   @NotNull HgFileRevision revision) {
    HgRevisionNumber vcsRevisionNumber = revision.getRevisionNumber();
    List<HgRevisionNumber> parents = vcsRevisionNumber.getParents();
    HgRevisionNumber firstParent = parents.isEmpty() ? null : parents.get(0); // can have no parents if it is a root
    List<Hash> parentsHash = new SmartList<>();
    for (HgRevisionNumber parent : parents) {
      parentsHash.add(factory.createHash(parent.getChangeset()));
    }

    final Collection<Change> changes = new ArrayList<>();
    for (String file : revision.getModifiedFiles()) {
      changes.add(createChange(project, root, file, firstParent, file, vcsRevisionNumber, FileStatus.MODIFIED));
    }
    for (String file : revision.getAddedFiles()) {
      changes.add(createChange(project, root, null, null, file, vcsRevisionNumber, FileStatus.ADDED));
    }
    for (String file : revision.getDeletedFiles()) {
      changes.add(createChange(project, root, file, firstParent, null, vcsRevisionNumber, FileStatus.DELETED));
    }
    for (Map.Entry<String, String> copiedFile : revision.getMovedFiles().entrySet()) {
      changes.add(createChange(project, root, copiedFile.getKey(), firstParent, copiedFile.getValue(), vcsRevisionNumber,
                               HgChangeProvider.RENAMED));
    }

    return factory.createFullDetails(factory.createHash(vcsRevisionNumber.getChangeset()), parentsHash,
                                     revision.getRevisionDate().getTime(), root,
                                     vcsRevisionNumber.getSubject(),
                                     vcsRevisionNumber.getName(), vcsRevisionNumber.getEmail(),
                                     vcsRevisionNumber.getCommitMessage(), vcsRevisionNumber.getName(),
                                     vcsRevisionNumber.getEmail(), revision.getRevisionDate().getTime(),
                                     () -> changes);
  }


  @Nullable
  public static HgCommandResult getLogResult(@NotNull final Project project,
                                             @NotNull final VirtualFile root, @NotNull HgVersion version, int limit,
                                             @NotNull List<String> parameters, @NotNull String template) {
    HgLogCommand hgLogCommand = new HgLogCommand(project);
    hgLogCommand.setLogFile(false);

    List<String> args = new ArrayList<>(parameters);
    if (!version.isParentRevisionTemplateSupported()) {
      args.add("--debug");
    }
    return hgLogCommand.execute(root, template, limit, getOriginalHgFile(project, root), args);
  }

  public static void readLog(@NotNull Project project, @NotNull VirtualFile root, @NotNull HgVersion version, int limit,
                             @NotNull List<String> hashes, @NotNull String template, @NotNull Consumer<StringBuilder> consumer)
    throws VcsException {
    HgLogCommand hgLogCommand = new HgLogCommand(project);
    hgLogCommand.setLogFile(false);

    ThrowableConsumer<List<String>, VcsException> logRunner = hashesChunk -> {
      HgLogOutputSplitter splitter = new HgLogOutputSplitter(consumer);
      List<String> args = new ArrayList<>(hashesChunk);
      if (!version.isParentRevisionTemplateSupported()) {
        args.add("--debug");
      }
      hgLogCommand.execute(root, template, limit, getOriginalHgFile(project, root), args, splitter);
      splitter.finish();
    };

    if (hashes.isEmpty()) {
      // no hashes provided means need to read the whole thing
      logRunner.consume(hashes);
    }
    else {
      VcsFileUtil.foreachChunk(hashes, 2, logRunner);
    }
  }

  public static HgFile getOriginalHgFile(@NotNull Project project, @NotNull VirtualFile root) {
    HgFile hgFile = new HgFile(root, VcsUtil.getFilePath(root.getPath()));
    if (project.isDisposed()) {
      return hgFile;
    }
    FilePath originalFileName = HgUtil.getOriginalFileName(hgFile.toFilePath(), ChangeListManager.getInstance(project));
    return new HgFile(hgFile.getRepo(), originalFileName);
  }

  @NotNull
  public static <CommitInfo> List<CommitInfo> getCommitRecords(@NotNull Project project,
                                                               @Nullable HgCommandResult result,
                                                               @NotNull Function<String, CommitInfo> converter) {
    return getCommitRecords(project, result, converter, false);
  }

  @NotNull
  public static <CommitInfo> List<CommitInfo> getCommitRecords(@NotNull Project project,
                                                               @Nullable HgCommandResult result,
                                                               @NotNull Function<String, CommitInfo> converter, boolean silent) {
    final List<CommitInfo> revisions = new LinkedList<>();
    if (result == null) {
      return revisions;
    }

    List<String> errors = result.getErrorLines();
    if (!errors.isEmpty()) {
      if (result.getExitValue() != 0) {
        if (silent) {
          LOG.debug(errors.toString());
        }
        else {
          VcsNotifier.getInstance(project).notifyError(HgVcsMessages.message("hg4idea.error.log.command.execution"), errors.toString());
        }
        return Collections.emptyList();
      }
      LOG.warn(errors.toString());
    }
    String output = result.getRawOutput();
    List<String> changeSets = StringUtil.split(output, HgChangesetUtil.CHANGESET_SEPARATOR);
    return ContainerUtil.mapNotNull(changeSets, converter);
  }

  @NotNull
  public static List<? extends VcsShortCommitDetails> readMiniDetails(@NotNull final Project project,
                                                                      @NotNull final VirtualFile root,
                                                                      @NotNull List<String> hashes)
    throws VcsException {
    final VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return Collections.emptyList();
    }

    HgVcs hgvcs = HgVcs.getInstance(project);
    assert hgvcs != null;
    final HgVersion version = hgvcs.getVersion();
    List<String> templateList = HgBaseLogParser.constructDefaultTemplate(version);
    templateList.add("{desc}");
    final String[] templates = ArrayUtil.toStringArray(templateList);

    return VcsFileUtil.foreachChunk(prepareHashes(hashes), 2,
                                    strings -> {
                                      HgCommandResult logResult =
                                        getLogResult(project, root, version, -1, strings, HgChangesetUtil.makeTemplate(templates));

                                      return getCommitRecords(project, logResult, new HgBaseLogParser<VcsShortCommitDetails>() {
                                        @Override
                                        protected VcsShortCommitDetails convertDetails(@NotNull String rev,
                                                                                       @NotNull String changeset,
                                                                                       @NotNull SmartList<HgRevisionNumber> parents,
                                                                                       @NotNull Date revisionDate,
                                                                                       @NotNull String author,
                                                                                       @NotNull String email,
                                                                                       @NotNull List<String> attributes) {
                                          String message = parseAdditionalStringAttribute(attributes, MESSAGE_INDEX);
                                          String subject = extractSubject(message);
                                          List<Hash> parentsHash = new SmartList<>();
                                          for (HgRevisionNumber parent : parents) {
                                            parentsHash.add(factory.createHash(parent.getChangeset()));
                                          }
                                          return factory
                                            .createShortDetails(factory.createHash(changeset), parentsHash, revisionDate.getTime(), root,
                                                                subject, author, email, author, email, revisionDate.getTime());
                                        }
                                      });
                                    });
  }

  @NotNull
  public static List<TimedVcsCommit> readAllHashes(@NotNull Project project, @NotNull VirtualFile root,
                                                   @NotNull final Consumer<VcsUser> userRegistry, @NotNull List<String> params)
    throws VcsException {

    final VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return Collections.emptyList();
    }
    HgVcs hgvcs = HgVcs.getInstance(project);
    assert hgvcs != null;
    HgVersion version = hgvcs.getVersion();
    String[] templates = ArrayUtil.toStringArray(HgBaseLogParser.constructDefaultTemplate(version));
    HgCommandResult result = getLogResult(project, root, version, -1, params, HgChangesetUtil.makeTemplate(templates));
    return getCommitRecords(project, result, new HgBaseLogParser<TimedVcsCommit>() {

      @Override
      protected TimedVcsCommit convertDetails(@NotNull String rev,
                                              @NotNull String changeset,
                                              @NotNull SmartList<HgRevisionNumber> parents,
                                              @NotNull Date revisionDate,
                                              @NotNull String author,
                                              @NotNull String email,
                                              @NotNull List<String> attributes) {
        List<Hash> parentsHash = new SmartList<>();
        for (HgRevisionNumber parent : parents) {
          parentsHash.add(factory.createHash(parent.getChangeset()));
        }
        userRegistry.consume(factory.createUser(author, email));
        return factory.createTimedCommit(factory.createHash(changeset),
                                         parentsHash, revisionDate.getTime());
      }
    });
  }

  @Nullable
  static VcsLogObjectsFactory getObjectsFactoryWithDisposeCheck(Project project) {
    if (!project.isDisposed()) {
      return ServiceManager.getService(project, VcsLogObjectsFactory.class);
    }
    return null;
  }

  @NotNull
  public static Change createChange(@NotNull Project project, @NotNull VirtualFile root,
                                    @Nullable String fileBefore,
                                    @Nullable HgRevisionNumber revisionBefore,
                                    @Nullable String fileAfter,
                                    HgRevisionNumber revisionAfter,
                                    FileStatus aStatus) {

    HgContentRevision beforeRevision =
      fileBefore == null || aStatus == FileStatus.ADDED ? null
                                                        : HgContentRevision
        .create(project, new HgFile(root, new File(root.getPath(), fileBefore)), revisionBefore);
    ContentRevision afterRevision;
    if (aStatus == FileStatus.DELETED) {
      afterRevision = null;
    }
    else if (revisionAfter == null && fileBefore != null) {
      afterRevision =
        CurrentContentRevision.create(new HgFile(root, new File(root.getPath(), fileAfter != null ? fileAfter : fileBefore)).toFilePath());
    }
    else {
      assert revisionAfter != null;
      afterRevision = fileAfter == null ? null :
                      HgContentRevision.create(project, new HgFile(root, new File(root.getPath(), fileAfter)), revisionAfter);
    }
    return new Change(beforeRevision, afterRevision, aStatus);
  }

  @NotNull
  public static List<String> prepareHashes(@NotNull List<String> hashes) {
    List<String> hashArgs = new ArrayList<>();
    for (String hash : hashes) {
      hashArgs.add("-r");
      hashArgs.add(hash);
    }
    return hashArgs;
  }

  @NotNull
  public static Collection<String> getDescendingHeadsOfBranches(@NotNull Project project, @NotNull VirtualFile root, @NotNull Hash hash)
    throws VcsException {
    //hg log -r "descendants(659db54c1b6865c97c4497fa867194bcd759ca76) and head()" --template "{branch}{bookmarks}"
    Set<String> branchHeads = new HashSet<>();
    List<String> params = new ArrayList<>();
    params.add("-r");
    params.add("descendants(" + hash.asString() + ") and head()");
    HgLogCommand hgLogCommand = new HgLogCommand(project);
    hgLogCommand.setLogFile(false);
    String template = HgChangesetUtil.makeTemplate("{branch}", "{bookmarks}");
    HgCommandResult logResult = hgLogCommand.execute(root, template, -1, null, params);
    if (logResult == null || logResult.getExitValue() != 0) {
      throw new VcsException("Couldn't get commit details: log command execution error.");
    }
    String output = logResult.getRawOutput();
    List<String> changeSets = StringUtil.split(output, HgChangesetUtil.CHANGESET_SEPARATOR);
    for (String line : changeSets) {
      List<String> attributes = StringUtil.split(line, HgChangesetUtil.ITEM_SEPARATOR);
      branchHeads.addAll(attributes);
    }
    return branchHeads;
  }

  public static String prepareParameter(String paramName, String value) {
    return "--" + paramName + "=" + value; // no value escaping needed, because the parameter itself will be quoted by GeneralCommandLine
  }

  private static class HgLogOutputSplitter extends HgLineProcessListener {
    @NotNull private final StringBuilder myOutput;
    private final Consumer<StringBuilder> myConsumer;

    public HgLogOutputSplitter(Consumer<StringBuilder> consumer) {
      myConsumer = consumer;
      myOutput = new StringBuilder();
    }

    @Override
    protected void processOutputLine(@NotNull String line) {
      int separatorIndex;
      while ((separatorIndex = line.indexOf(HgChangesetUtil.CHANGESET_SEPARATOR)) >= 0) {
        myOutput.append(line.substring(0, separatorIndex));
        myConsumer.consume(myOutput);
        myOutput.setLength(0); // maybe also call myOutput.trimToSize() to free some memory ?
        line = line.substring(separatorIndex + 1);
      }
      myOutput.append(line);
    }

    public void finish() throws VcsException {
      super.finish();
      if (myOutput.length() != 0) {
        myConsumer.consume(myOutput);
        myOutput.setLength(0);
      }
    }
  }
}
