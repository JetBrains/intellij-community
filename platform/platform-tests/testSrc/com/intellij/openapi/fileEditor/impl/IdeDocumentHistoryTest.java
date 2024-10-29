// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.mock.Mock;
import com.intellij.openapi.components.ComponentManagerEx;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IdeDocumentHistoryTest extends HeavyPlatformTestCase {
  private IdeDocumentHistoryImpl myHistory;

  private Mock.MyFileEditor  mySelectedEditor;
  private Mock.MyVirtualFile mySelectedFile;
  private FileEditorState myEditorState;
  private FileEditorProvider myProvider;

  private MyState myState1;
  private MyState myState2;
  private MyState myState3;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mySelectedEditor = new Mock.MyFileEditor() {
      @Override
      public @NotNull FileEditorState getState(@NotNull FileEditorStateLevel level) {
        return myEditorState;
      }

      @Override
      public void setState(@NotNull FileEditorState state) {
        myEditorState = state;
      }

      @Override
      public @NotNull VirtualFile getFile() {
        return mySelectedFile;
      }
    };

    EditorManager editorManager = new EditorManager();
    Project project = getProject();
    myHistory = new IdeDocumentHistoryImpl(project, ((ComponentManagerEx)project).getCoroutineScope()) {
      @Override
      protected FileEditorManagerEx getFileEditorManager() {
        return editorManager;
      }

      @Override
      protected FileEditorWithProvider getSelectedEditor() {
        return mySelectedEditor == null ? null : new FileEditorWithProvider(mySelectedEditor, myProvider);
      }

      @Override
      protected void executeCommand(@NotNull Runnable runnable, String name, Object groupId) {
        myHistory.onCommandStarted(groupId);
        runnable.run();
        myHistory.onSelectionChanged();
        myHistory.onCommandFinished(getProject(), groupId);
      }
    };

    mySelectedFile = new Mock.MyVirtualFile();
    myEditorState = new MyState(false, "start");
    myProvider = new Mock.MyFileEditorProvider() {
      @Override
      public @NotNull String getEditorTypeId() {
        return "EditorType";
      }
    };
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Disposer.dispose(myHistory);
      myHistory = null;
      mySelectedEditor = null;
      myEditorState = null;
      myProvider = null;
      mySelectedFile = null;
      myState1 = null;
      myState2 = null;
      myState3 = null;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testNoHistoryRecording() {
    myHistory.onCommandStarted(null);
    myHistory.onCommandFinished(getProject(), null);

    assertFalse(myHistory.isBackAvailable());
    assertFalse(myHistory.isForwardAvailable());
  }

  public void testNavigationRecording() {
    makeNavigationChange(myState2);

    assertTrue(myHistory.isBackAvailable());
    assertFalse(myHistory.isForwardAvailable());

    assertEquals(1, myHistory.getBackPlaces().size());
  }

  public void testMergingForwardPlaces() {
    myEditorState = new MyState(true, "state1");
    makeNavigationChange(new MyState(true, "state2"));

    assertTrue(myHistory.isBackAvailable());
    assertFalse(myHistory.isForwardAvailable());

    assertEquals(1, myHistory.getBackPlaces().size());
  }

  public void testSimpleNavigation() {
    pushTwoStates();

    assertFalse(myHistory.isForwardAvailable());
    assertTrue(myHistory.isBackAvailable());

    myHistory.back();
    assertTrue(myHistory.isBackAvailable());
    assertTrue(myHistory.isForwardAvailable());
    assertSame(myState2, myEditorState);

    myHistory.back();
    assertFalse(myHistory.isBackAvailable());
    assertTrue(myHistory.isForwardAvailable());
    assertSame(myState1, myEditorState);

    myHistory.forward();
    assertTrue(myHistory.isBackAvailable());
    assertTrue(myHistory.isForwardAvailable());
    assertSame(myState2, myEditorState);

    myHistory.forward();
    assertTrue(myHistory.isBackAvailable());
    assertFalse(myHistory.isForwardAvailable());
    assertSame(myState3, myEditorState);
  }

  public void testQueueCutOff() {
    pushTwoStates();
    myHistory.back();

    assertTrue(myHistory.isBackAvailable());
    assertTrue(myHistory.isForwardAvailable());

    MyState newState = new MyState(false, "newState");
    makeNavigationChange(newState);

    assertTrue(myHistory.isBackAvailable());
    assertFalse(myHistory.isForwardAvailable());

    myHistory.back();
    assertSame(myState2, myEditorState);
    myHistory.back();
    assertSame(myState1, myEditorState);
    assertFalse(myHistory.isBackAvailable());
  }

  public void testRemoveInvalid() {
    pushTwoStates();
    assertTrue(myHistory.isBackAvailable());

    mySelectedFile.myValid = false;
    myHistory.removeInvalidFilesFromStacks();

    assertFalse(myHistory.isBackAvailable());
    assertFalse(myHistory.isForwardAvailable());
  }

  public void testRemoveOptionallyIncludedFiles() {
    var file = new MyOptionallyIncludedFile();
    mySelectedFile = file;

    pushTwoStates();
    assertTrue(myHistory.isBackAvailable());

    file.myIsIncludedInDocumentHistory = false;
    myHistory.removeInvalidFilesFromStacks();

    assertFalse(myHistory.isBackAvailable());
    assertFalse(myHistory.isForwardAvailable());
  }

  public void testOptionallyExcludedFileIsNotPushed() {
    var file = new MyOptionallyIncludedFile();
    file.myIsIncludedInDocumentHistory = false;
    mySelectedFile = file;

    pushTwoStates();
    assertFalse(myHistory.isBackAvailable());
    assertFalse(myHistory.isForwardAvailable());
  }

  public void testExcludedFileIsNotPushed() {
    mySelectedFile = new MyAlwaysExcludedFile();

    pushTwoStates();
    assertFalse(myHistory.isBackAvailable());
    assertFalse(myHistory.isForwardAvailable());
  }

  private void pushTwoStates() {
    myState1 = new MyState(false, "state1");
    myState2 = new MyState(false, "state2");
    myState3 = new MyState(false, "state3");

    myEditorState = myState1;
    makeNavigationChange(myState2);
    makeNavigationChange(myState3);
  }

  private void makeNavigationChange(MyState newState) {
    myHistory.onCommandStarted(null);
    myHistory.onSelectionChanged();
    myHistory.onCommandFinished(getProject(), null);
    myEditorState = newState;
  }

  private final class EditorManager extends Mock.MyFileEditorManager {
    @Override
    public @NotNull FileEditorComposite openFile(@NotNull VirtualFile file,
                                                 @Nullable EditorWindow window,
                                                 @NotNull FileEditorOpenOptions options) {
      return FileEditorComposite.Companion.fromPair(new Pair<>(new FileEditor[]{mySelectedEditor}, new FileEditorProvider[]{myProvider}));
    }

    @Override
    public FileEditorProvider getProvider(FileEditor editor) {
      return myProvider;
    }
  }

  private static final class MyState implements FileEditorState {
    private final boolean myCanBeMerged;
    private final String myName;

    MyState(boolean canBeMerged, String name) {
      myCanBeMerged = canBeMerged;
      myName = name;
    }

    @Override
    public boolean canBeMergedWith(@NotNull FileEditorState otherState, @NotNull FileEditorStateLevel level) {
      return myCanBeMerged;
    }

    public String toString() {
      return myName;
    }
  }

  private static final class MyAlwaysExcludedFile extends Mock.MyVirtualFile implements IdeDocumentHistoryImpl.SkipFromDocumentHistory {
  }

  private static final class MyOptionallyIncludedFile extends Mock.MyVirtualFile implements IdeDocumentHistoryImpl.OptionallyIncluded {
    boolean myIsIncludedInDocumentHistory = true;

    @Override
    public boolean isIncludedInDocumentHistory(@NotNull Project project) {
      return myIsIncludedInDocumentHistory;
    }
  }
}
