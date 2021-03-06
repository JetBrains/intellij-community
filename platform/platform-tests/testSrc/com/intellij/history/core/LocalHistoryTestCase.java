// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.core;

import com.intellij.history.core.changes.*;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.storage.TestContent;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.FileEntry;
import com.intellij.history.core.tree.RootEntry;
import com.intellij.history.integration.TestVirtualFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Clock;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.TestLoggerFactory;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestRule;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public abstract class LocalHistoryTestCase extends Assert {
  @Rule
  public TestRule watcher = TestLoggerFactory.createTestWatcher();

  private static long myCurrentId = 0;
  private static IdeaProjectTestFixture fixture; // to initialize FSRecords

  public static long nextId() {
    return myCurrentId++;
  }

  protected static byte[] b(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  protected static Content c(String data) {
    return data == null ? null : new TestContent(b(data));
  }

  @BeforeClass
  public static void setupFSRecords() throws Exception {
    EdtTestUtil.runInEdtAndWait(() -> {
      fixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder("ddd").getFixture();
      fixture.setUp();
      ApplicationManager.getApplication().runWriteAction(() -> {
        assert fixture.getProject() != null;
      });
    });
  }

  @AfterClass
  public static void tearDownFSRecords() {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      try {
        fixture.tearDown();
        fixture = null;
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  public CreateFileChange createFile(RootEntry root, String path) {
    return createFile(root, path, null);
  }

  public CreateFileChange createFile(RootEntry root, String path, String content) {
    return createFile(root, path, content, -1, false);
  }

  public CreateFileChange createFile(RootEntry root, String path, String content, long timestamp, boolean isReadOnly) {
    root.ensureDirectoryExists(Paths.getParentOf(path)).addChild(new FileEntry(Paths.getNameOf(path), c(content), timestamp, isReadOnly));
    return new CreateFileChange(nextId(), path);
  }

  public CreateDirectoryChange createDirectory(RootEntry root, String path) {
    root.ensureDirectoryExists(path);
    return new CreateDirectoryChange(nextId(), path);
  }

  public ContentChange changeContent(RootEntry root, String path, String content) {
    return changeContent(root, path, content, -1);
  }

  public ContentChange changeContent(RootEntry root, String path, String content, long timestamp) {
    Entry e = root.getEntry(path);
    ContentChange result = new ContentChange(nextId(), path, e.getContent(), e.getTimestamp());
    e.setContent(c(content), timestamp);
    return result;
  }

  public ROStatusChange changeROStatus(RootEntry root, String path, boolean status) {
    Entry e = root.getEntry(path);
    ROStatusChange result = new ROStatusChange(nextId(), path, e.isReadOnly());
    e.setReadOnly(status);
    return result;
  }

  public RenameChange rename(RootEntry root, String path, String newName) {
    Entry e = root.getEntry(path);
    RenameChange result = new RenameChange(nextId(), Paths.renamed(path, newName), e.getName());
    e.setName(newName);
    return result;
  }

  public MoveChange move(RootEntry root, String path, String newParent) {
    Entry e = root.getEntry(path);
    MoveChange result = new MoveChange(nextId(), Paths.reparented(path, newParent), e.getParent().getPath());
    e.getParent().removeChild(e);
    root.getEntry(newParent).addChild(e);
    return result;
  }

  public DeleteChange delete(RootEntry root, String path) {
    Entry e = root.getEntry(path);
    e.getParent().removeChild(e);
    return new DeleteChange(nextId(), path, e);
  }

  public <T extends StructuralChange> T add(LocalHistoryFacade vcs, T change) {
    vcs.beginChangeSet();
    vcs.addChangeInTests(change);
    vcs.endChangeSet(null);
    return change;
  }

  public ChangeSet addChangeSet(LocalHistoryFacade facade, Change... changes) {
    return addChangeSet(facade, null, changes);
  }

  public ChangeSet addChangeSet(LocalHistoryFacade facade, String changeSetName, Change... changes) {
    facade.beginChangeSet();
    for (Change each : changes) {
      if (each instanceof StructuralChange) {
        facade.addChangeInTests((StructuralChange)each);
      }
      else {
        facade.putLabelInTests((PutLabelChange)each);
      }
    }
    facade.endChangeSet(changeSetName);
    return facade.getChangeListInTests().getChangesInTests().get(0);
  }

  public static List<Revision> collectRevisions(LocalHistoryFacade facade, RootEntry root, String path, String projectId, @Nullable String pattern) {
    return new RevisionsCollector(facade, root, path, projectId, pattern).getResult();
  }

  public static List<ChangeSet> collectChanges(LocalHistoryFacade facade, String path, String projectId, String pattern) {
    ChangeCollectingVisitor v = new ChangeCollectingVisitor(path, projectId, pattern);
    facade.accept(v);
    return v.getChanges();
  }

  @SafeVarargs
  public static <T> T[] array(T @NotNull ... objects) {
    return objects;
  }

  @SafeVarargs
  public static <T> List<T> list(T @NotNull ... objects) {
    return Arrays.asList(objects);
  }

  protected static ChangeSet cs(Change... changes) {
    return cs(null, changes);
  }

  protected static ChangeSet cs(String name, Change... changes) {
    return cs(0, name, changes);
  }

  protected static ChangeSet cs(long timestamp, String name, Change... changes) {
    ChangeSet result = new ChangeSet(nextId(), timestamp);
    result.setName(name);
    for (Change each : changes) {
      result.addChange(each);
    }
    return result;
  }

  protected static void setCurrentTimestamp(long t) {
    Clock.setTime(t);
  }

  protected static void assertContent(String expected, Entry e) {
    assertContent(expected, e.getContent());
  }

  protected static void assertContent(String expected, Content c) {
    assertEquals(expected, new String(c.getBytes(), StandardCharsets.UTF_8));
  }

  protected static void assertEquals(Object[] expected, Collection actual) {
    Assert.assertArrayEquals(actual.toString(), expected, actual.toArray());
  }

  protected static TestVirtualFile testDir(String name) {
    return new TestVirtualFile(name);
  }

  protected static TestVirtualFile testFile(String name) {
    return testFile(name, "");
  }

  protected static TestVirtualFile testFile(String name, String content) {
    return testFile(name, content, -1);
  }

  protected static TestVirtualFile testFile(String name, String content, long timestamp) {
    return testFile(name, content, timestamp, false);
  }

  protected static TestVirtualFile testFile(String name, String content, long timestamp, boolean isReadOnly) {
    return new TestVirtualFile(name, content, timestamp, isReadOnly);
  }
}
