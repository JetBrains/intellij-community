// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.mock.MockLocalFileSystem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.projectlevelman.FileWatchRequestsManager;
import com.intellij.openapi.vcs.impl.projectlevelman.NewMappings;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.RunAll;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author irengrig
 */
public class VcsFileWatchRequestManagementTest extends LightPlatformTestCase {
  private static final String ourVcsName = "vcs";

  private NewMappings myNewMappings;
  private MyMockLocalFileSystem myMockLocalFileSystem;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    Project project = getProject();
    myNewMappings = new NewMappings(project, (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(project));
    Disposer.register(getTestRootDisposable(), myNewMappings);
    myMockLocalFileSystem = new MyMockLocalFileSystem();
    myNewMappings.setFileWatchRequestsManager(new TestFileWatchRequestsManager(project, myNewMappings, myMockLocalFileSystem));
    myNewMappings.activateActiveVcses();
  }

  @Override
  protected void tearDown() {
    new RunAll()
      .append(() -> myMockLocalFileSystem.disposed())
      .append(() -> super.tearDown())
      .run();
  }

  public void testAdd() {
    final String path = "/a/b/c";
    myMockLocalFileSystem.add(path);

    myNewMappings.setMapping("", ourVcsName);
    myNewMappings.setMapping(path, ourVcsName);
    // add twice -> nothing happens
    myNewMappings.setMapping(path, ourVcsName);
  }

  public void testAddRemove() {
    final String path = "/a/b/c";

    myMockLocalFileSystem.add(path);
    myNewMappings.setMapping(path, ourVcsName);

    myMockLocalFileSystem.remove(path);
    myNewMappings.removeDirectoryMapping(new VcsDirectoryMapping(path, ourVcsName));
  }

  public void testAddSwitch() {
    final String path = "/a/b/c";
    myMockLocalFileSystem.add(path);
    myNewMappings.setMapping(path, ourVcsName);

    myMockLocalFileSystem.add(path);
    myMockLocalFileSystem.remove(path);
    myNewMappings.setMapping(path, "scv");
  }

  public void testAddSwitchRemoveAdd() {
    final String path = "/a/b/c";
    final String path2 = "/a1/b1/c1";
    myMockLocalFileSystem.add(path);
    myMockLocalFileSystem.add(path2);
    myNewMappings.setMapping(path, ourVcsName);
    myNewMappings.setMapping(path2, ourVcsName);

    // switch
    myMockLocalFileSystem.add(path);
    myMockLocalFileSystem.remove(path);
    myNewMappings.setMapping(path, "scv");

    // remove
    myMockLocalFileSystem.remove(path2);
    myNewMappings.removeDirectoryMapping(new VcsDirectoryMapping(path2, ourVcsName));

    // add back
    myMockLocalFileSystem.add(path2);
    myNewMappings.setMapping(path2, ourVcsName);
  }

  public void testSets() {
    final String path = "/a/b/c";
    final String path2 = "/a2/b2/c2";
    final String path3 = "/a3/b3/c3";
    final String path4 = "/a4/b4/c4";
    final String path5 = "/a5/b5/c5";

    final String anotherVcs = "another";

    myMockLocalFileSystem.add(path);
    myMockLocalFileSystem.add(path2);
    myMockLocalFileSystem.add(path3);
    myMockLocalFileSystem.add(path4);

    myNewMappings.setDirectoryMappings(Arrays.asList(new VcsDirectoryMapping(path, ourVcsName),
                                                     new VcsDirectoryMapping(path2, ourVcsName),
                                                     new VcsDirectoryMapping(path3, anotherVcs),
                                                     new VcsDirectoryMapping(path4, anotherVcs)));

    // set another
    myMockLocalFileSystem.remove(path2);
    myMockLocalFileSystem.remove(path3);
    myMockLocalFileSystem.remove(path4);
    myMockLocalFileSystem.add(path5);
    myNewMappings.setDirectoryMappings(Arrays.asList(new VcsDirectoryMapping(path, ourVcsName),
                                                     new VcsDirectoryMapping(path5, anotherVcs)));
  }

  private static final class MyMockLocalFileSystem extends MockLocalFileSystem {
    private final Set<String> myAdd;
    private final Set<String> myRemove;
    private boolean myDisposed;

    private MyMockLocalFileSystem() {
      myAdd = new HashSet<>();
      myRemove = new HashSet<>();
    }

    @NotNull
    @Override
    public Set<WatchRequest> replaceWatchedRoots(@NotNull Collection<WatchRequest> watchRequests,
                                                 @Nullable Collection<String> recursiveRoots,
                                                 @Nullable Collection<String> flatRoots) {
      assertNullOrEmpty(flatRoots);

      if (myDisposed) {
        assertNullOrEmpty(recursiveRoots);
        return Collections.emptySet();
      }
      else {
        Set<String> oldPaths = ContainerUtil.map2Set(watchRequests, it -> it.getRootPath());
        Set<String> newPaths = new HashSet<>(recursiveRoots);

        Set<String> toRemove = new HashSet<>(oldPaths);
        toRemove.removeAll(newPaths);
        for (String rootPath : toRemove) {
          assertTrue(myRemove.remove(rootPath));
        }

        Set<String> toAdd = new HashSet<>(newPaths);
        toAdd.removeAll(oldPaths);
        for (String rootPath : toAdd) {
          assertTrue(myAdd.remove(rootPath));
        }

        return ContainerUtil.map2Set(newPaths, rootPath -> new MockKey(rootPath, true));
      }
    }

    public void add(final String path) {
      assertFalse(myDisposed);
      myAdd.add(path);
    }

    public void remove(final String path) {
      assertFalse(myDisposed);
      myRemove.add(path);
    }

    public void disposed() {
      myDisposed = true;
    }
  }

  // should be, as originals, compared by references
  private static class MockKey implements LocalFileSystem.WatchRequest {
    private final String myPath;
    private final boolean myRecursively;

    MockKey(String path, boolean recursively) {
      myPath = path;
      myRecursively = recursively;
    }

    @NotNull
    @Override
    public String getRootPath() {
      return myPath;
    }

    @Override
    public boolean isToWatchRecursively() {
      return myRecursively;
    }
  }

  private static class TestFileWatchRequestsManager extends FileWatchRequestsManager {
    TestFileWatchRequestsManager(@NotNull Project project, @NotNull NewMappings newMappings, @NotNull LocalFileSystem localFileSystem) {
      super(project, newMappings, localFileSystem);
    }

    @Override
    public void ping() {
      pingImmediately();
    }
  }
}