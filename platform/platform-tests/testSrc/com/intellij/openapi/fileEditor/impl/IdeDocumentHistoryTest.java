/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor.impl;

import com.intellij.mock.Mock;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.annotations.NotNull;

public class IdeDocumentHistoryTest extends PlatformTestCase {
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
    myHistory = new IdeDocumentHistoryImpl(getProject(), EditorFactory.getInstance(), new EditorManager(), VirtualFileManager.getInstance(), CommandProcessor.getInstance(), new Mock.MyToolWindowManager()) {
      @Override
      protected Pair<FileEditor,FileEditorProvider> getSelectedEditor() {
        return Pair.create ((FileEditor)mySelectedEditor, myProvider);
      }

      @Override
      protected void executeCommand(Runnable runnable, String name, Object groupId) {
        myHistory.onCommandStarted();
        runnable.run();
        myHistory.onSelectionChanged();
        myHistory.onCommandFinished(groupId);
      }
    };

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
    myHistory = null;
    mySelectedEditor = null;
    myEditorState = null;
    myProvider = null;
    mySelectedFile = null;
    myState1 = null;
    myState2 = null;
    myState3 = null;
    super.tearDown();
  }

  public void testNoHistoryRecording() {
    myHistory.onCommandStarted();
    myHistory.onCommandFinished(null);

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

    myHistory.onFileDeleted();

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
    myHistory.onCommandFinished(null);
    myEditorState = newState;
  }

  private class EditorManager extends Mock.MyFileEditorManager {

    @Override
    public VirtualFile getFile(@NotNull FileEditor editor) {
      return mySelectedFile;
    }

    @Override
    @NotNull
    public Pair<FileEditor[],FileEditorProvider[]> openFileWithProviders(@NotNull VirtualFile file,
                                                                         boolean focusEditor,
                                                                         boolean searchForSplitter) {
      return Pair.create (new FileEditor[] {mySelectedEditor}, new FileEditorProvider[] {myProvider});
    }

    @Override
    public FileEditorProvider getProvider(FileEditor editor) {
      return myProvider;
    }
  }

  private static class MyState implements FileEditorState {

    private final boolean myCanBeMerged;
    private final String myName;

    public MyState(boolean canBeMerged, String name) {
      myCanBeMerged = canBeMerged;
      myName = name;
    }

    @Override
    public boolean canBeMergedWith(FileEditorState otherState, FileEditorStateLevel level) {
      return myCanBeMerged;
    }

    public String toString() {
      return myName;
    }
  }

}
