// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.vcs.log.impl.VcsChangesLazilyParsedDetails;
import com.intellij.vcs.log.impl.VcsFileStatusInfo;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
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
import java.util.stream.Collectors;

public class HgHistoryUtil {

  private static final Logger LOG = Logger.getInstance(HgHistoryUtil.class);

  private HgHistoryUtil() {
  }

  @NotNull
  public static List<VcsCommitMetadata> loadMetadata(@NotNull final Project project,
                                                     @NotNull final VirtualFile root, int limit,
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
    HgBaseLogParser<VcsCommitMetadata> baseParser = new HgBaseLogParser<VcsCommitMetadata>() {

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
  public static List<VcsFullCommitDetails> history(@NotNull Project project, @NotNull VirtualFile root, int limit,
                                                   @NotNull List<String> hashParameters, boolean silent)
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
        VcsNotifier.getInstance(project).notifyError(HgBundle.message("hg4idea.error.log.command.execution"), e.getMessage());
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
    List<List<VcsFileStatusInfo>> reportedChanges = new ArrayList<>();
    reportedChanges.add(getStatusInfo(revision));

    HgRevisionNumber vcsRevisionNumber = revision.getRevisionNumber();
    List<? extends HgRevisionNumber> parents = vcsRevisionNumber.getParents();
    for (HgRevisionNumber parent : parents.stream().skip(1).collect(Collectors.toList())) {
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

  @NotNull
  protected static List<VcsFileStatusInfo> getChangesFromParent(@NotNull Project project, @NotNull VirtualFile root,
                                                                @NotNull HgRevisionNumber commit, @NotNull HgRevisionNumber parent) {
    HgStatusCommand status = new HgStatusCommand.Builder(true).ignored(false).unknown(false).copySource(true)
      .baseRevision(parent).targetRevision(commit).build(project);
    return convertHgChanges(status.executeInCurrentThread(root));
  }

  @NotNull
  private static List<VcsFileStatusInfo> getStatusInfo(@NotNull HgFileRevision revision) {
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

  @NotNull
  private static List<VcsFileStatusInfo> convertHgChanges(@NotNull Set<HgChange> changes) {
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
        case DELETED:
          firstPath = change.beforeFile().getRelativePath();
          secondPath = null;
          if (copied.contains(firstPath)) continue; // file was renamed
          break;
        case MOVED:
          firstPath = change.beforeFile().getRelativePath();
          secondPath = change.afterFile().getRelativePath();
          if (!deleted.contains(firstPath)) {
            type = Change.Type.NEW; // file was copied, treating it like an addition
            firstPath = change.afterFile().getRelativePath();
            secondPath = null;
          }
          break;
        case MODIFICATION:
        case NEW:
        default:
          firstPath = change.afterFile().getRelativePath();
          secondPath = null;
          break;
      }
      result.add(new VcsFileStatusInfo(type, Objects.requireNonNull(firstPath), secondPath));
    }
    return result;
  }

  @Nullable
  private static Change.Type getType(@NotNull HgFileStatusEnum status) {
    switch (status) {
      case ADDED:
        return Change.Type.NEW;
      case MODIFIED:
        return Change.Type.MODIFICATION;
      case DELETED:
        return Change.Type.DELETED;
      case COPY:
        return Change.Type.MOVED;
      case UNVERSIONED:
      case MISSING:
      case UNMODIFIED:
      case IGNORED:
        return null;
    }
    return null;
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

  @NotNull
  public static <CommitInfo> List<CommitInfo> getCommitRecords(@NotNull Project project,
                                                               @Nullable HgCommandResult result,
                                                               @NotNull Function<? super String, ? extends CommitInfo> converter) {
    return getCommitRecords(project, result, converter, false);
  }

  @NotNull
  public static <CommitInfo> List<CommitInfo> getCommitRecords(@NotNull Project project,
                                                               @Nullable HgCommandResult result,
                                                               @NotNull Function<? super String, ? extends CommitInfo> converter,
                                                               boolean silent) {
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
          VcsNotifier.getInstance(project).notifyError(HgBundle.message("hg4idea.error.log.command.execution"), errors.toString());
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
  public static List<? extends VcsCommitMetadata> readCommitMetadata(@NotNull final Project project,
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
    final String[] templates = ArrayUtilRt.toStringArray(templateList);

    return VcsFileUtil.foreachChunk(prepareHashes(hashes), 2,
                                    strings -> {
                                      HgCommandResult logResult =
                                        getLogResult(project, root, version, -1, strings, HgChangesetUtil.makeTemplate(templates));

                                      return getCommitRecords(project, logResult, new HgBaseLogParser<VcsCommitMetadata>() {
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
                                          return factory
                                            .createCommitMetadata(factory.createHash(changeset), parentsHash, revisionDate.getTime(), root,
                                                                  subject, author, email, message, author, email, revisionDate.getTime());
                                        }
                                      });
                                    });
  }

  @NotNull
  public static List<TimedVcsCommit> readAllHashes(@NotNull Project project, @NotNull VirtualFile root,
                                                   @NotNull Consumer<? super VcsUser> userRegistry, @NotNull List<String> params) {
    return readHashes(project, root, userRegistry, -1, params);
  }

  @NotNull
  public static List<TimedVcsCommit> readHashes(@NotNull Project project, @NotNull VirtualFile root,
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
    return getCommitRecords(project, result, new HgBaseLogParser<TimedVcsCommit>() {

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
      if (myOutput.length() != 0) {
        myConsumer.consume(myOutput);
        myOutput.setLength(0);
      }
    }
  }
}
