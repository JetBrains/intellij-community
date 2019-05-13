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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.mock.MockLocalFileSystem;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.projectlevelman.FileWatchRequestsManager;
import com.intellij.openapi.vcs.impl.projectlevelman.NewMappings;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author irengrig
 */
public class VcsFileWatchRequestManagementTest extends PlatformTestCase {
  private static final String ourVcsName = "vcs";

  private NewMappings myNewMappings;
  private MyMockLocalFileSystem myMockLocalFileSystem;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    ProjectLevelVcsManagerImpl vcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(myProject);
    myNewMappings = new NewMappings(myProject, vcsManager, FileStatusManager.getInstance(myProject));
    myMockLocalFileSystem = new MyMockLocalFileSystem();
    myNewMappings.setFileWatchRequestsManager(new FileWatchRequestsManager(myProject, myNewMappings, myMockLocalFileSystem));
    myNewMappings.activateActiveVcses();
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

  private static class MyMockLocalFileSystem extends MockLocalFileSystem {
    private final Set<String> myAdd;
    private final Set<String> myRemove;

    private MyMockLocalFileSystem() {
      myAdd = new HashSet<>();
      myRemove = new HashSet<>();
    }

    @NotNull
    @Override
    public Set<WatchRequest> replaceWatchedRoots(@NotNull Collection<? extends WatchRequest> watchRequests,
                                                 @Nullable Collection<String> recursiveRoots,
                                                 @Nullable Collection<String> flatRoots) {
      for (WatchRequest watchRequest : watchRequests) {
        assertTrue(myRemove.remove(watchRequest.getRootPath()));
      }

      Set<WatchRequest> requests = new HashSet<>();

      if (recursiveRoots != null) {
        for (String rootPath : recursiveRoots) {
          assertTrue(myAdd.remove(rootPath));
          requests.add(new MockKey(rootPath, true));
        }
      }

      if (flatRoots != null) {
        for (String rootPath : flatRoots) {
          assertTrue(myAdd.remove(rootPath));
          requests.add(new MockKey(rootPath, false));
        }
      }

      return requests;
    }

    public void add(final String path) {
      myAdd.add(path);
    }

    public void remove(final String path) {
      myRemove.add(path);
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
}