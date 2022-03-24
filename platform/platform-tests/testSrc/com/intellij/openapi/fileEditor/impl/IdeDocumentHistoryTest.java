// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.mock.Mock;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import org.jetbrains.annotations.NotNull;

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
      @NotNull
      public FileEditorState getState(@NotNull FileEditorStateLevel level) {
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
    myHistory = new IdeDocumentHistoryImpl(getProject()) {
      @Override
      protected FileEditorManagerEx getFileEditorManager() {
        return editorManager;
      }

      @Override
      protected FileEditorWithProvider getSelectedEditor() {
        return mySelectedEditor == null ? null : new FileEditorWithProvider(mySelectedEditor, myProvider);
      }

      @Override
      protected void executeCommand(Runnable runnable, String name, Object groupId) {
        myHistory.onCommandStarted();
        runnable.run();
        myHistory.onSelectionChanged();
        myHistory.onCommandFinished(getProject(), groupId);
      }
    };

    mySelectedFile = new Mock.MyVirtualFile();
    myEditorState = new MyState(false, "start");
    myProvider = new Mock.MyFileEditorProvider() {
      @Override
      @NotNull
      public String getEditorTypeId() {
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
    myHistory.onCommandStarted();
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


  private void pushTwoStates() {
    myState1 = new MyState(false, "state1");
    myState2 = new MyState(false, "state2");
    myState3 = new MyState(false, "state3");

    myEditorState = myState1;
    makeNavigationChange(myState2);
    makeNavigationChange(myState3);
  }

  private void makeNavigationChange(MyState newState) {
    myHistory.onCommandStarted();
    myHistory.onSelectionChanged();
    myHistory.onCommandFinished(getProject(), null);
    myEditorState = newState;
  }

  private class EditorManager extends Mock.MyFileEditorManager {
    @Override
    public VirtualFile getFile(@NotNull FileEditor editor) {
      return mySelectedFile;
    }

    @Override
    @NotNull
    public Pair<FileEditor[], FileEditorProvider[]> openFileWithProviders(@NotNull VirtualFile file, boolean focusEditor, boolean searchForSplitter) {
      return Pair.create(new FileEditor[]{mySelectedEditor}, new FileEditorProvider[]{myProvider});
    }

    @Override
    public FileEditorProvider getProvider(FileEditor editor) {
      return myProvider;
    }
  }

  private static class MyState implements FileEditorState {

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
}
