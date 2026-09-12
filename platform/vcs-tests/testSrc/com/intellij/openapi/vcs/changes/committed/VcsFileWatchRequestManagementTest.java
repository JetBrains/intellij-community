// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.components.ComponentManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.projectlevelman.NewMappings;
import com.intellij.openapi.vcs.impl.projectlevelman.VcsMappingsFileWatchesManager;
import com.intellij.openapi.vfs.WatchRoots;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.RunAll;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class VcsFileWatchRequestManagementTest extends LightPlatformTestCase {
  private static final String ourVcsName = "vcs";

  private NewMappings myNewMappings;
  private MockWatchRoots myWatchRoots;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    Project project = getProject();
    myNewMappings = new NewMappings(project, (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(project), ((ComponentManagerEx)project).getCoroutineScope());
    Disposer.register(getTestRootDisposable(), myNewMappings);
    myWatchRoots = new MockWatchRoots();
    myNewMappings.setFileWatchRequestsManager(new TestVcsMappingsFileWatchesManager(project, myNewMappings, myWatchRoots));
    myNewMappings.activateActiveVcses();
  }

  @Override
  protected void tearDown() {
    new RunAll(
      () -> myWatchRoots.disposed(),
      () -> super.tearDown()
    ).run();
  }

  public void testAdd() {
    final String path = "/a/b/c";
    myWatchRoots.add(path);

    myNewMappings.setMapping("", ourVcsName);
    myNewMappings.setMapping(path, ourVcsName);
    // add twice -> nothing happens
    myNewMappings.setMapping(path, ourVcsName);
  }

  public void testAddRemove() {
    final String path = "/a/b/c";

    myWatchRoots.add(path);
    myNewMappings.setMapping(path, ourVcsName);

    myWatchRoots.remove(path);
    myNewMappings.removeDirectoryMapping(new VcsDirectoryMapping(path, ourVcsName));
  }

  public void testAddSwitch() {
    final String path = "/a/b/c";
    myWatchRoots.add(path);
    myNewMappings.setMapping(path, ourVcsName);

    myWatchRoots.add(path);
    myWatchRoots.remove(path);
    myNewMappings.setMapping(path, "scv");
  }

  public void testAddSwitchRemoveAdd() {
    final String path = "/a/b/c";
    final String path2 = "/a1/b1/c1";
    myWatchRoots.add(path);
    myWatchRoots.add(path2);
    myNewMappings.setMapping(path, ourVcsName);
    myNewMappings.setMapping(path2, ourVcsName);

    // switch
    myWatchRoots.add(path);
    myWatchRoots.remove(path);
    myNewMappings.setMapping(path, "scv");

    // remove
    myWatchRoots.remove(path2);
    myNewMappings.removeDirectoryMapping(new VcsDirectoryMapping(path2, ourVcsName));

    // add back
    myWatchRoots.add(path2);
    myNewMappings.setMapping(path2, ourVcsName);
  }

  public void testSets() {
    final String path = "/a/b/c";
    final String path2 = "/a2/b2/c2";
    final String path3 = "/a3/b3/c3";
    final String path4 = "/a4/b4/c4";
    final String path5 = "/a5/b5/c5";

    final String anotherVcs = "another";

    myWatchRoots.add(path);
    myWatchRoots.add(path2);
    myWatchRoots.add(path3);
    myWatchRoots.add(path4);

    myNewMappings.setDirectoryMappings(Arrays.asList(new VcsDirectoryMapping(path, ourVcsName),
                                                     new VcsDirectoryMapping(path2, ourVcsName),
                                                     new VcsDirectoryMapping(path3, anotherVcs),
                                                     new VcsDirectoryMapping(path4, anotherVcs)));

    // set another
    myWatchRoots.remove(path2);
    myWatchRoots.remove(path3);
    myWatchRoots.remove(path4);
    myWatchRoots.add(path5);
    myNewMappings.setDirectoryMappings(Arrays.asList(new VcsDirectoryMapping(path, ourVcsName),
                                                     new VcsDirectoryMapping(path5, anotherVcs)));
  }

  private static final class MockWatchRoots implements WatchRoots {
    private final Set<String> myAdd;
    private final Set<String> myRemove;
    private final Map<String, Integer> myOpen;
    private boolean myDisposed;

    private MockWatchRoots() {
      myAdd = new HashSet<>();
      myRemove = new HashSet<>();
      myOpen = new HashMap<>();
    }

    @Override
    public @NotNull Token watch(@NotNull String rootPath, boolean recursive) {
      assertTrue(recursive);
      assertFalse(myDisposed);
      if (myOpen.merge(rootPath, 1, Integer::sum) == 1) {
        assertTrue(myAdd.remove(rootPath));
      }
      return new MockKey(rootPath);
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

    // should be, as originals, compared by references
    private final class MockKey implements Token {
      private final String myPath;

      MockKey(String path) {
        myPath = path;
      }

      @Override
      public void close() {
        if (myOpen.merge(myPath, -1, Integer::sum) == 0) {
          myOpen.remove(myPath);
          if (!myDisposed) assertTrue(myRemove.remove(myPath));
        }
      }
    }
  }

  private static class TestVcsMappingsFileWatchesManager extends VcsMappingsFileWatchesManager {
    TestVcsMappingsFileWatchesManager(@NotNull Project project,
                                      @NotNull NewMappings newMappings,
                                      @NotNull WatchRoots watchRoots) {
      super(project, newMappings, watchRoots);
    }

    @Override
    public void ping() {
      pingImmediately();
    }
  }
}
