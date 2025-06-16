// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.log;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
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
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.impl.VcsChangesLazilyParsedDetails;
import com.intellij.vcs.log.impl.VcsFileStatusInfo;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.*;
import org.zmlx.hg4idea.command.HgLogCommand;
import org.zmlx.hg4idea.command.HgStatusCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.HgLineProcessListener;
import org.zmlx.hg4idea.util.HgChangesetUtil;
import org.zmlx.hg4idea.util.HgUtil;
import org.zmlx.hg4idea.util.HgVersion;

import java.io.File;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.zmlx.hg4idea.HgNotificationIdsHolder.LOG_CMD_EXEC_ERROR;

public final class HgHistoryUtil {

  private static final Logger LOG = Logger.getInstance(HgHistoryUtil.class);

  private HgHistoryUtil() {
  }

  public static @NotNull List<VcsCommitMetadata> loadMetadata(final @NotNull Project project,
                                                              final @NotNull VirtualFile root, int limit,
                                                              @NotNull List<String> parameters) {

    final VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return Collections.emptyList();
    }
    HgVcs hgvcs = HgVcs.getInstance(project);
    assert hgvcs != null;
    HgVersion version = hgvcs.getVersion();
    List<String> templateList = HgBaseLogParser.constructDefaultTemplate(version);
    templateList.add("{desc}");
    String[] templates = ArrayUtilRt.toStringArray(templateList);
    HgCommandResult result = getLogResult(project, root, version, limit, parameters, HgChangesetUtil.makeTemplate(templates));
    HgBaseLogParser<VcsCommitMetadata> baseParser = createMetadataParser(root, factory);
    return getCommitRecords(project, result, baseParser);
  }

  /**
   * <p>Get & parse hg log detailed output with commits, their parents and their changes.
   * For null destination return log command result</p>
   * <p/>
   * <p>Warning: this is method is efficient by speed, but don't query too much, because the whole log output is retrieved at once,
   * and it can occupy too much memory. The estimate is ~600Kb for 1000 commits.</p>
   */
  public static @NotNull List<VcsFullCommitDetails> history(@NotNull Project project, @NotNull VirtualFile root, int limit,
                                                            @NotNull List<@NonNls String> hashParameters, boolean silent)
    throws VcsException {
    HgVcs hgvcs = HgVcs.getInstance(project);
    assert hgvcs != null;
    HgVersion version = hgvcs.getVersion();
    String[] templates = HgBaseLogParser.constructFullTemplateArgument(true, version);

    List<VcsFullCommitDetails> result = new ArrayList<>();
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
        VcsNotifier.getInstance(project).notifyError(LOG_CMD_EXEC_ERROR,
                                                     HgBundle.message("hg4idea.error.log.command.execution"),
                                                     e.getMessage());
      }
      throw e;
    }
    return result;
  }

  public static @NotNull List<? extends VcsFullCommitDetails> createFullCommitsFromResult(@NotNull Project project,
                                                                                          @NotNull VirtualFile root,
                                                                                          @Nullable HgCommandResult result,
                                                                                          @NotNull HgVersion version) throws VcsException {
    final VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return Collections.emptyList();
    }
    List<HgFileRevision> hgRevisions =
      getCommitRecordsOrFail(project, result, new HgFileRevisionLogParser(project, getOriginalHgFile(project, root), version));
    List<VcsFullCommitDetails> vcsFullCommitDetailsList = new ArrayList<>();
    for (HgFileRevision revision : hgRevisions) {
      vcsFullCommitDetailsList.add(createDetails(project, root, factory, revision));
    }
    return vcsFullCommitDetailsList;
  }

  public static @NotNull VcsFullCommitDetails createDetails(@NotNull Project project,
                                                            @NotNull VirtualFile root,
                                                            @NotNull VcsLogObjectsFactory factory,
                                                            @NotNull HgFileRevision revision) {
    List<List<VcsFileStatusInfo>> reportedChanges = new ArrayList<>();
    reportedChanges.add(getStatusInfo(revision));

    HgRevisionNumber vcsRevisionNumber = revision.getRevisionNumber();
    List<? extends HgRevisionNumber> parents = vcsRevisionNumber.getParents();
    for (HgRevisionNumber parent : parents.stream().skip(1).toList()) {
      reportedChanges.add(getChangesFromParent(project, root, vcsRevisionNumber, parent));
    }

    Hash hash = factory.createHash(vcsRevisionNumber.getChangeset());
    List<Hash> parentsHashes = ContainerUtil.map(parents, p -> factory.createHash(p.getChangeset()));
    long time = revision.getRevisionDate().getTime();
    VcsUser author = factory.createUser(vcsRevisionNumber.getName(), vcsRevisionNumber.getEmail());
    return new VcsChangesLazilyParsedDetails(project, hash, parentsHashes, time, root, vcsRevisionNumber.getSubject(), author,
                                             vcsRevisionNumber.getCommitMessage(), author,
                                             time, reportedChanges, new HgChangesParser(vcsRevisionNumber));
  }

  private static @NotNull List<VcsFileStatusInfo> getChangesFromParent(@NotNull Project project, @NotNull VirtualFile root,
                                                                       @NotNull HgRevisionNumber commit, @NotNull HgRevisionNumber parent) {
    HgStatusCommand status = new HgStatusCommand.Builder(true).ignored(false).unknown(false).copySource(true)
      .baseRevision(parent).targetRevision(commit).build(project);
    return convertHgChanges(status.executeInCurrentThread(root));
  }

  private static @NotNull List<VcsFileStatusInfo> getStatusInfo(@NotNull HgFileRevision revision) {
    List<VcsFileStatusInfo> firstParentChanges = new ArrayList<>();
    for (String file : revision.getModifiedFiles()) {
      firstParentChanges.add(new VcsFileStatusInfo(Change.Type.MODIFICATION, file, null));
    }
    for (String file : revision.getAddedFiles()) {
      firstParentChanges.add(new VcsFileStatusInfo(Change.Type.NEW, file, null));
    }
    for (String file : revision.getDeletedFiles()) {
      firstParentChanges.add(new VcsFileStatusInfo(Change.Type.DELETED, file, null));
    }
    for (Map.Entry<String, String> copiedFile : revision.getMovedFiles().entrySet()) {
      firstParentChanges.add(new VcsFileStatusInfo(Change.Type.MOVED, copiedFile.getKey(), copiedFile.getValue()));
    }
    return firstParentChanges;
  }

  private static @NotNull List<VcsFileStatusInfo> convertHgChanges(@NotNull Set<HgChange> changes) {
    Set<String> deleted = new HashSet<>();
    Set<String> copied = new HashSet<>();

    for (HgChange change : changes) {
      Change.Type type = getType(change.getStatus());
      if (Change.Type.DELETED.equals(type)) {
        deleted.add(change.beforeFile().getRelativePath());
      }
      else if (Change.Type.MOVED.equals(type)) {
        copied.add(change.beforeFile().getRelativePath());
      }
    }

    List<VcsFileStatusInfo> result = new ArrayList<>();
    for (HgChange change : changes) {
      Change.Type type = getType(change.getStatus());
      LOG.assertTrue(type != null, "Unsupported status for change " + change);

      String firstPath;
      String secondPath;
      switch (type) {
        case DELETED -> {
          firstPath = change.beforeFile().getRelativePath();
          secondPath = null;
          if (copied.contains(firstPath)) continue; // file was renamed
        }
        case MOVED -> {
          firstPath = change.beforeFile().getRelativePath();
          secondPath = change.afterFile().getRelativePath();
          if (!deleted.contains(firstPath)) {
            type = Change.Type.NEW; // file was copied, treating it like an addition
            firstPath = change.afterFile().getRelativePath();
            secondPath = null;
          }
        }
        //case MODIFICATION, NEW ->
        default -> {
          firstPath = change.afterFile().getRelativePath();
          secondPath = null;
        }
      }
      result.add(new VcsFileStatusInfo(type, Objects.requireNonNull(firstPath), secondPath));
    }
    return result;
  }

  private static @Nullable Change.Type getType(@NotNull HgFileStatusEnum status) {
    return switch (status) {
      case ADDED -> Change.Type.NEW;
      case MODIFIED -> Change.Type.MODIFICATION;
      case DELETED -> Change.Type.DELETED;
      case COPY -> Change.Type.MOVED;
      case UNVERSIONED, MISSING, UNMODIFIED, IGNORED -> null;
    };
  }


  public static @Nullable HgCommandResult getLogResult(final @NotNull Project project,
                                                       final @NotNull VirtualFile root, @NotNull HgVersion version, int limit,
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
                             @NotNull List<String> hashes, @NotNull String template, @NotNull Consumer<? super StringBuilder> consumer)
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

  public static @NotNull <CommitInfo> List<CommitInfo> getCommitRecords(@NotNull Project project,
                                                                        @Nullable HgCommandResult result,
                                                                        @NotNull Function<? super String, ? extends CommitInfo> converter) {
    try {
      return getCommitRecordsOrFail(project, result, converter);
    }
    catch (VcsException e) {
      VcsNotifier.getInstance(project).notifyError(LOG_CMD_EXEC_ERROR,
                                                   HgBundle.message("hg4idea.error.log.command.execution"),
                                                   e.getMessage());
      return Collections.emptyList();
    }
  }

  public static @NotNull <CommitInfo> List<CommitInfo> getCommitRecordsOrFail(@NotNull Project project,
                                                                              @Nullable HgCommandResult result,
                                                                              @NotNull Function<? super String, ? extends CommitInfo> converter
  ) throws VcsException {
    final List<CommitInfo> revisions = new LinkedList<>();
    if (result == null) {
      return revisions;
    }

    List<@NlsSafe String> errors = result.getErrorLines();
    if (!errors.isEmpty()) {
      if (result.getExitValue() != 0) {
        throw new VcsException(errors.toString()); //NON-NLS
      }
      LOG.warn(errors.toString());
    }
    String output = result.getRawOutput();
    List<String> changeSets = StringUtil.split(output, HgChangesetUtil.CHANGESET_SEPARATOR);
    return changeSets.stream()
      .map(converter)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  public static void readCommitMetadata(@NotNull Project project,
                                        @NotNull VirtualFile root,
                                        @NotNull List<String> hashes,
                                        @NotNull Consumer<? super VcsCommitMetadata> consumer) throws VcsException {
    final VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) return;

    HgVcs hgvcs = HgVcs.getInstance(project);
    assert hgvcs != null;
    final HgVersion version = hgvcs.getVersion();
    List<String> templateList = HgBaseLogParser.constructDefaultTemplate(version);
    templateList.add("{desc}");
    final String[] templates = ArrayUtilRt.toStringArray(templateList);

    HgBaseLogParser<VcsCommitMetadata> parser = createMetadataParser(root, factory);

    readLog(project, root, hgvcs.getVersion(), -1,
            prepareHashes(hashes),
            HgChangesetUtil.makeTemplate(templates),
            stringBuilder -> {
              VcsCommitMetadata metadata = parser.convert(stringBuilder.toString());
              if (metadata != null) {
                consumer.consume(metadata);
              }
            });
  }

  private static @NotNull HgBaseLogParser<VcsCommitMetadata> createMetadataParser(@NotNull VirtualFile root, VcsLogObjectsFactory factory) {
    return new HgBaseLogParser<>() {

      @Override
      protected VcsCommitMetadata convertDetails(@NotNull String rev,
                                                 @NotNull String changeset,
                                                 @NotNull SmartList<? extends HgRevisionNumber> parents,
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
  }

  public static @NotNull List<TimedVcsCommit> readAllHashes(@NotNull Project project, @NotNull VirtualFile root,
                                                            @NotNull Consumer<? super VcsUser> userRegistry, @NotNull List<String> params) {
    return readHashes(project, root, userRegistry, -1, params);
  }

  public static @NotNull List<TimedVcsCommit> readHashes(@NotNull Project project, @NotNull VirtualFile root,
                                                         @NotNull Consumer<? super VcsUser> userRegistry, int limit,
                                                         @NotNull List<String> params) {
    final VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return Collections.emptyList();
    }
    HgVcs hgvcs = HgVcs.getInstance(project);
    assert hgvcs != null;
    HgVersion version = hgvcs.getVersion();
    String[] templates = ArrayUtilRt.toStringArray(HgBaseLogParser.constructDefaultTemplate(version));
    HgCommandResult result = getLogResult(project, root, version, limit, params, HgChangesetUtil.makeTemplate(templates));
    return getCommitRecords(project, result, new HgBaseLogParser<>() {

      @Override
      protected TimedVcsCommit convertDetails(@NotNull String rev,
                                              @NotNull String changeset,
                                              @NotNull SmartList<? extends HgRevisionNumber> parents,
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

  static @Nullable VcsLogObjectsFactory getObjectsFactoryWithDisposeCheck(Project project) {
    if (!project.isDisposed()) {
      return project.getService(VcsLogObjectsFactory.class);
    }
    return null;
  }

  public static @NotNull Change createChange(@NotNull Project project, @NotNull VirtualFile root,
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

  public static @NotNull List<String> prepareHashes(@NotNull List<String> hashes) {
    List<String> hashArgs = new ArrayList<>();
    for (String hash : hashes) {
      hashArgs.add("-r");
      hashArgs.add(hash);
    }
    return hashArgs;
  }

  public static @NotNull Collection<String> getDescendingHeadsOfBranches(@NotNull Project project,
                                                                         @NotNull VirtualFile root,
                                                                         @NotNull Hash hash)
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
      throw new VcsException(HgBundle.message("error.history.cant.get.commit.details.log.command.error"));
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
    private final @NotNull StringBuilder myOutput;
    private final Consumer<? super StringBuilder> myConsumer;

    HgLogOutputSplitter(Consumer<? super StringBuilder> consumer) {
      myConsumer = consumer;
      myOutput = new StringBuilder();
    }

    @Override
    protected void processOutputLine(@NotNull String line) {
      int separatorIndex;
      while ((separatorIndex = line.indexOf(HgChangesetUtil.CHANGESET_SEPARATOR)) >= 0) {
        myOutput.append(line, 0, separatorIndex);
        myConsumer.consume(myOutput);
        myOutput.setLength(0); // maybe also call myOutput.trimToSize() to free some memory ?
        line = line.substring(separatorIndex + 1);
      }
      myOutput.append(line);
    }

    @Override
    public void finish() throws VcsException {
      super.finish();
      if (!myOutput.isEmpty()) {
        myConsumer.consume(myOutput);
        myOutput.setLength(0);
      }
    }
  }
}
