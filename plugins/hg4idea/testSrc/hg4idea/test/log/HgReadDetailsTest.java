// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package hg4idea.test.log;

import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsFullCommitDetails;
import hg4idea.test.HgPlatformTest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.command.HgWorkingCopyRevisionsCommand;
import org.zmlx.hg4idea.log.HgLogProvider;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.intellij.openapi.vcs.Executor.cd;
import static com.intellij.openapi.vcs.Executor.overwrite;
import static hg4idea.test.HgExecutor.hg;
import static hg4idea.test.log.HgUserFilterTest.findLogProvider;

public class HgReadDetailsTest extends HgPlatformTest {
  private HgLogProvider myProvider;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myProvider = findLogProvider(myProject);
    cd(myProject.getBaseDir());
  }

  @Override
  public void tearDown() {
    myProvider = null;
    super.tearDown();
  }

  public void testReadFullDetailsByHash() throws IOException, VcsException {
    Map<String, String> commits = generateCommits();
    List<VcsFullCommitDetails> details = new ArrayList<>();
    myProvider.readFullDetails(projectRoot, new ArrayList<>(commits.keySet()), details::add);
    assertSameElements(ContainerUtil.map(details, d -> d.getFullMessage() + "\n" + getChanges(d)), commits.values());
  }

  @NotNull
  private static String getChanges(@NotNull VcsFullCommitDetails details) {
    StringBuilder sb = new StringBuilder();
    for (Change change : details.getChanges()) {
      appendFileChange(sb, change.getType(), callIfNotNull(change.getBeforeRevision(), (ContentRevision cr) -> cr.getFile().getName()),
                       callIfNotNull(change.getAfterRevision(), (ContentRevision cr) -> cr.getFile().getName()));
    }
    return sb.toString();
  }

  @Nullable
  private static <T, R> R callIfNotNull(@Nullable T argument, @NotNull Function<T, R> converter) {
    if (argument == null) return null;
    return converter.apply(argument);
  }

  @NotNull
  public Map<String, String> generateCommits() throws IOException {
    Map<String, String> commits = new LinkedHashMap<>();
    int nCommits = 10;
    for (int i = 0; i < nCommits; i++) {
      Couple<String> commit = commit(i);
      commits.put(commit.first, commit.second);
    }
    assertEquals("Expected to create " + nCommits + " but created " + commits, nCommits, commits.size());
    return commits;
  }

  @NotNull
  private Couple<String> commit(int i) throws IOException {
    StringBuilder changedFiles = new StringBuilder();

    String file = "original.txt";
    String renamed = "renamed.txt";
    String copied = "copied.txt";

    if (getFile(file).exists()) {
      renameFile(file, renamed, changedFiles);
    }
    else if (getFile(renamed).exists() && !getFile(copied).exists()) {
      copyFile(renamed, copied, changedFiles);
    }
    else if (getFile(copied).exists()) {
      deleteFile(copied, changedFiles);
      deleteFile(renamed, changedFiles);
    }
    else {
      addFile(file, changedFiles);
    }

    myProject.getBaseDir().refresh(false, true);

    String message = "commit " + i + " subject\n\ncommit " + i + " body";
    hg("commit -m '" + message + "'");

    return Couple.of(new HgWorkingCopyRevisionsCommand(myProject).tip(myProject.getBaseDir()).getChangeset(),
                     message + "\n" + changedFiles);
  }

  @NotNull
  private File getFile(@NotNull String name) {
    return new File(myProject.getBasePath(), name);
  }

  private static void renameFile(@NotNull String original, @NotNull String renamed, @NotNull StringBuilder changedFiles) {
    hg("mv " + original + " " + renamed);
    appendFileChange(changedFiles, Change.Type.MOVED, original, renamed);
  }

  private static void copyFile(@NotNull String original, @NotNull String copied, @NotNull StringBuilder changedFiles) {
    hg("cp " + original + " " + copied);
    appendFileChange(changedFiles, Change.Type.NEW, null, copied);
  }

  private static void deleteFile(@NotNull String file, @NotNull StringBuilder changedFiles) {
    hg("rm " + file);
    appendFileChange(changedFiles, Change.Type.DELETED, file, null);
  }

  private static void addFile(@NotNull String file, @NotNull StringBuilder changedFiles) throws IOException {
    overwrite(file, "content" + Math.random());
    appendFileChange(changedFiles, Change.Type.NEW, null, file);
    hg("add " + file);
  }

  private static void appendFileChange(@NotNull StringBuilder sb,
                                       @NotNull Change.Type type,
                                       @Nullable String before,
                                       @Nullable String after) {
    switch (type) {
      case MODIFICATION:
        sb.append("M ").append(after);
        break;
      case NEW:
        sb.append("A ").append(after);
        break;
      case DELETED:
        sb.append("D ").append(before);
        break;
      case MOVED:
        sb.append("R ").append(before).append(" -> ").append(after);
        break;
    }
    sb.append("\n");
  }
}
