/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import java.util.Collection;
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
  public void tearDown() throws Exception {
    myProvider = null;
    super.tearDown();
  }

  public void testReadAllFullDetails() throws IOException, VcsException {
    Collection<String> commits = generateCommits().values();
    List<VcsFullCommitDetails> details = ContainerUtil.newArrayList();
    myProvider.readAllFullDetails(projectRoot, details::add);
    assertSameElements(ContainerUtil.map(details.subList(0, details.size() - 1), // removing initial commit
                                         d -> d.getFullMessage() + "\n" + getChanges(d)), commits);
  }

  public void testReadFullDetailsByHash() throws IOException, VcsException {
    Map<String, String> commits = generateCommits();
    List<VcsFullCommitDetails> details = ContainerUtil.newArrayList();
    myProvider.readFullDetails(projectRoot, ContainerUtil.newArrayList(commits.keySet()), details::add);
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
    Map<String, String> commits = ContainerUtil.newLinkedHashMap();
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
                     message + "\n" + changedFiles.toString());
  }

  @NotNull
  private File getFile(@NotNull String name) {
    return new File(myProject.getBaseDir().getPath(), name);
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
