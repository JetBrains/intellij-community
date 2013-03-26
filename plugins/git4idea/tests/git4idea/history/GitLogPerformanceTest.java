/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package git4idea.history;

import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.ColumnInfo;
import git4idea.history.browser.CachedRefs;
import git4idea.history.browser.ChangesFilter;
import git4idea.history.wholeTree.*;
import git4idea.test.GitTest;
import org.jetbrains.annotations.Nullable;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/10/13
 * Time: 1:38 AM
 */
public class GitLogPerformanceTest extends GitTest {
  private final static String ourPath = "C:/git/IDEA";
  private final static String ourPath1 = "C:/git/IDEA/community";
  private final static String ourPath2 = "C:/git/IDEA/contrib";

  @Test
  public void testGitLogLoaderPerformance() throws Exception {
    VirtualFile root;
    Assert.assertNotNull((root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(ourPath))));
    final RootsHolder rootsHolder = new RootsHolder(Arrays.asList(root));
    final GitLogFilters filters = new GitLogFilters();
    impl(rootsHolder, filters);
  }

  @Test
  public void testOneWithName() throws Exception {
    VirtualFile root;
    Assert.assertNotNull((root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(ourPath))));
    final RootsHolder rootsHolder = new RootsHolder(Arrays.asList(root));
    final GitLogFilters filters = createFiltersWihName();
    impl(rootsHolder, filters);
  }

  @Test
  public void testAll() throws Exception {
    VirtualFile root;
    Assert.assertNotNull((root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(ourPath))));
    VirtualFile root1;
    Assert.assertNotNull((root1 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(ourPath))));
    VirtualFile root2;
    Assert.assertNotNull((root2 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(ourPath))));
    final RootsHolder rootsHolder = new RootsHolder(Arrays.asList(root, root1, root2));

    final GitLogFilters filters = new GitLogFilters();
    impl(rootsHolder, filters);
  }

  @Test
  public void testAllIdeaReposWithNames() throws Exception {
    VirtualFile root;
    Assert.assertNotNull((root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(ourPath))));
    VirtualFile root1;
    Assert.assertNotNull((root1 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(ourPath))));
    VirtualFile root2;
    Assert.assertNotNull((root2 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(ourPath))));
    final RootsHolder rootsHolder = new RootsHolder(Arrays.asList(root, root1, root2));

    final GitLogFilters filters = createFiltersWihName();
    impl(rootsHolder, filters);
  }

  private GitLogFilters createFiltersWihName() {
    final Set<ChangesFilter.Filter> userFilters = new HashSet<ChangesFilter.Filter>();
    userFilters.add(new ChangesFilter.Committer("irengrig"));
    userFilters.add(new ChangesFilter.Author("irengrig"));

    return new GitLogFilters(null, userFilters, Collections.<ChangesFilter.Filter>emptySet(), null, null);
  }

  private void impl(RootsHolder rootsHolder, GitLogFilters filters) {
    final GitCommitsSequentialIndex commitsSequentially = new GitCommitsSequentialIndex();
    final MediatorImpl mediator = new MediatorImpl(myProject, commitsSequentially);
    final LoadController controller = new LoadController(myProject, mediator, null, commitsSequentially);

    mediator.setLoader(controller);
    final BigTableTableModel tableModel = new BigTableTableModel(Collections.<ColumnInfo>emptyList(), EmptyRunnable.getInstance());
    mediator.setTableModel(tableModel);
    tableModel.setRootsHolder(rootsHolder);
    final Semaphore semaphore = new Semaphore();
    final MyUIRefresh refresh = new MyUIRefresh(semaphore);
    mediator.setUIRefresh(refresh);

    final long start = System.currentTimeMillis();
    semaphore.down();
    mediator.reload(rootsHolder, Collections.<String>emptyList(), Collections.<String>emptyList(), filters, true);
    semaphore.waitFor(300000);
    refresh.assertMe();
    final long end = System.currentTimeMillis();
    System.out.println("Time: " + (end - start));
  }

  private static class MyUIRefresh implements UIRefresh {
    private final Semaphore mySemaphore;
    private volatile boolean myFinished;

    private MyUIRefresh(Semaphore semaphore) {
      mySemaphore = semaphore;
    }

    @Override
    public void linesReloaded(boolean drawMore) {
      if (drawMore) {
        finished();
      }
    }

    @Override
    public void detailsLoaded() {
    }

    @Override
    public void acceptException(Exception e) {
      throw new RuntimeException(e);
    }

    public void assertMe() {
      Assert.assertTrue(myFinished);
    }

    @Override
    public void finished() {
      myFinished = true;
      mySemaphore.up();
    }

    @Override
    public void reportStash(VirtualFile root, @Nullable Pair<AbstractHash, AbstractHash> hash) {
    }

    @Override
    public void reportSymbolicRefs(VirtualFile root, CachedRefs symbolicRefs) {
    }
  }
}
