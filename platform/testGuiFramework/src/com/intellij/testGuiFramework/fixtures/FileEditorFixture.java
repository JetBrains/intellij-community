/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.testGuiFramework.fixtures;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testGuiFramework.framework.Timeouts;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.timing.Condition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.reflect.core.Reflection.method;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.fest.swing.timing.Pause.pause;
import static org.fest.util.Strings.quote;
import static org.junit.Assert.*;

public class FileEditorFixture extends EditorFixture {

  private final FileEditorManager myManager;
  private final IdeFrameFixture myFrame;
  private final EditorTabsFixture tabs;

  public FileEditorFixture(Robot robot, IdeFrameFixture frame) {
    super(robot, null);
    myManager = FileEditorManager.getInstance(frame.getProject());
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() {
        FileEditorFixture.this.setEditor(myManager.getSelectedTextEditor());
      }
    });
    myFrame = frame;
    tabs = new EditorTabsFixture(robot, frame);
  }

  /**
   * Returns the current file being shown in the editor, if there is a current
   * editor open and it's a file editor
   *
   * @return the currently edited file or null
   */
  @Nullable
  public VirtualFile getCurrentFile() {
    return execute(new GuiQuery<VirtualFile>() {
      @javax.annotation.Nullable
      @Override
      protected VirtualFile executeInEDT() throws Throwable {
        VirtualFile[] selectedFiles = myManager.getSelectedFiles();
        if (selectedFiles.length > 0) {

          // we should be sure that EditorComponent is already showing
          VirtualFile selectedFile = selectedFiles[0];
          if (myManager.getEditors(selectedFile).length == 0) {
            return null;
          }
          else {
            FileEditor editor = myManager.getEditors(selectedFile)[0];
            return editor.getComponent().isShowing() ? selectedFile : null;
          }
        }

        return null;
      }
    });
  }

  /**
   * Returns the name of the current file, if any. Convenience method
   * for {@link #getCurrentFile()}.getName().
   *
   * @return the current file name, or null
   */
  @Nullable
  public String getCurrentFileName() {
    return execute(new GuiQuery<String>() {
      @Override
      protected String executeInEDT() throws Throwable {
        VirtualFile currentFile = getCurrentFile();
        return currentFile != null ? currentFile.getName() : null;
      }
    });
  }

  /**
   * Selects the given tab in the current editor. Used to switch between
   * design mode and editor mode for example.
   *
   * @param tabName the label in the editor, or null for the default (first) tab
   */
  public EditorFixture selectEditorView(@Nullable final String tabName) {
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() {
        VirtualFile currentFile = getCurrentFile();
        assertNotNull("Can't switch to tab " + tabName + " when no file is open in the editor", currentFile);
        FileEditor[] editors = myManager.getAllEditors(currentFile);
        FileEditor target = null;
        for (FileEditor editor : editors) {
          if (tabName == null || tabName.equals(editor.getName())) {
            target = editor;
            break;
          }
        }
        if (target != null) {
          // Have to use reflection
          //FileEditorManagerImpl#setSelectedEditor(final FileEditor editor)
          method("setSelectedEditor").withParameterTypes(FileEditor.class).in(myManager).invoke(target);
          return;
        }
        List<String> tabNames = new ArrayList<>();
        for (FileEditor editor : editors) {
          tabNames.add(editor.getName());
        }
        fail("Could not find editor tab \"" + (tabName != null ? tabName : "<default>") + "\": Available tabs = " + tabNames);
      }
    });
    return this;
  }

  /**
   * Opens up a different file. This will run through the "Open File..." dialog to
   * find and select the given file.
   *
   * @param file the file to open
   * @param tab  which tab to open initially, if there are multiple editors
   */
  public EditorFixture open(@NotNull final VirtualFile file, @NotNull final Tab tab) {
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() {
        // TODO: Use UI to navigate to the file instead
        Project project = myFrame.getProject();
        if (tab == Tab.EDITOR) {
          myManager.openTextEditor(new OpenFileDescriptor(project, file), true);
        }
        else {
          myManager.openFile(file, true);
        }
      }
    });

    pause(new Condition("File " + quote(file.getPath()) + " to be opened") {
      @Override
      public boolean test() {
        //noinspection ConstantConditions
        return execute(new GuiQuery<Boolean>() {
          @Override
          protected Boolean executeInEDT() {
            FileEditor[] editors = FileEditorManager.getInstance(myFrame.getProject()).getEditors(file);
            if (editors.length == 0) return false;
            return editors[0].getComponent().isShowing();
          }
        });
      }
    }, Timeouts.INSTANCE.getMinutes02());

    // TODO: Maybe find a better way to keep Documents in sync with their VirtualFiles.
    invokeActionViaKeystroke("Synchronize");

    return this;
  }

  /**
   * Opens up a different file. This will run through the "Open File..." dialog to
   * find and select the given file.
   *
   * @param relativePath the project-relative path (with /, not File.separator, as the path separator)
   * @param tab  which tab to open initially, if there are multiple editors
   */
  public EditorFixture open(@NotNull final String relativePath, @NotNull Tab tab) {
    assertFalse("Should use '/' in test relative paths, not File.separator", relativePath.contains("\\"));
    VirtualFile file = myFrame.findFileByRelativePath(relativePath, true);
    return open(file, tab);
  }


  /**
   * Selects the given tab in the current editor. Used to switch between
   * design mode and editor mode for example.
   *
   * @param tab the tab to switch to
   */
  public EditorFixture selectEditorView(@NotNull final Tab tab) {
    switch (tab) {
      case EDITOR:
        selectEditorView("Text");
        break;
      case DESIGN:
        selectEditorView("Design");
        break;
      case DEFAULT:
        selectEditorView((String)null);
        break;
      default:
        fail("Unknown tab " + tab);
    }
    return this;
  }

  /**
   * Like {@link #open(String, Tab)} but
   * always uses the default tab
   *
   * @param relativePath the project-relative path (with /, not File.separator, as the path separator)
   */
  public EditorFixture open(@NotNull final String relativePath) {
    return open(relativePath, Tab.DEFAULT);
  }

  @NotNull
  private FileFixture getCurrentFileFixture() {
    VirtualFile currentFile = getCurrentFile();
    assertNotNull("Expected a file to be open", currentFile);
    return new FileFixture(myFrame.getProject(), currentFile);
  }


  /**
   * Waits until the editor has the given number of errors at the given severity.
   * Typically used when you want to invoke an intention action, but need to wait until
   * the code analyzer has found an error it needs to resolve first.
   *
   * @param severity the severity of the issues you want to count
   * @param expected the expected count
   * @return this
   */
  @NotNull
  public EditorFixture waitForCodeAnalysisHighlightCount(@NotNull final HighlightSeverity severity, int expected) {
    FileFixture file = getCurrentFileFixture();
    file.waitForCodeAnalysisHighlightCount(severity, expected);
    return this;
  }

  @NotNull
  public EditorFixture waitUntilErrorAnalysisFinishes() {
    FileFixture file = execute(new GuiQuery<FileFixture>() {
      @Override
      protected FileFixture executeInEDT() {
        return getCurrentFileFixture();
      }
    });
    assert file != null;
    file.waitUntilErrorAnalysisFinishes();
    return this;
  }

  /**
   * An Editor could load files async, sometimes we should wait a bit when the virtual
   * file for a current editor will be set.
   *
   * @return FileFixture for loaded virtual file
   */
  @NotNull
  public FileFixture waitUntilFileIsLoaded() {
    Ref<VirtualFile> virtualFileReference = new Ref<>();
    pause(new Condition("Wait when virtual file is created...") {
      @Override
      public boolean test() {
        virtualFileReference.set(execute(new GuiQuery<VirtualFile>() {
          @Override
          protected VirtualFile executeInEDT() {
            return getCurrentFile();
          }
        }));
        return virtualFileReference.get() != null;
      }
    }, Timeouts.INSTANCE.getSeconds30());
    return new FileFixture(myFrame.getProject(), virtualFileReference.get());
  }


  /**
   * Checks that the editor has a given number of issues. This is a convenience wrapper
   * for {@link FileFixture#requireCodeAnalysisHighlightCount(HighlightSeverity, int)}
   *
   * @param severity the severity of the issues you want to count
   * @param expected the expected count
   * @return this
   */
  @NotNull
  public EditorFixture requireCodeAnalysisHighlightCount(@NotNull HighlightSeverity severity, int expected) {
    FileFixture file = getCurrentFileFixture();
    file.requireCodeAnalysisHighlightCount(severity, expected);
    return this;
  }

  @NotNull
  public EditorFixture requireHighlights(HighlightSeverity severity, String... highlights) {
    List<String> infos = new ArrayList<>();
    for (HighlightInfo info : getCurrentFileFixture().getHighlightInfos(severity)) {
      infos.add(info.getDescription());
    }
    assertThat(infos).containsOnly((Object[])highlights);
    return this;
  }


  /**
   * Requires the source editor's current file name to be the given name (or if null, for there
   * to be no current file)
   */
  public void requireName(@Nullable String name) {
    VirtualFile currentFile = getCurrentFile();
    if (name == null) {
      assertNull("Expected editor to not have an open file, but is showing " + currentFile, currentFile);
    }
    else if (currentFile == null) {
      fail("Expected file " + name + " to be showing, but the editor is not showing anything");
    }
    else {
      assertEquals(name, currentFile.getName());
    }
  }

  /**
   * Requires the source editor's current file to be in the given folder (or if null, for there
   * to be no current file)
   */
  public void requireFolderName(@Nullable String name) {
    VirtualFile currentFile = getCurrentFile();
    if (name == null) {
      assertNull("Expected editor to not have an open file, but is showing " + currentFile, currentFile);
    }
    else if (currentFile == null) {
      fail("Expected file " + name + " to be showing, but the editor is not showing anything");
    }
    else {
      VirtualFile parent = currentFile.getParent();
      assertNotNull("File " + currentFile.getName() + " does not have a parent", parent);
      assertEquals(name, parent.getName());
    }
  }

  /**
   * Selects the editor with a given tab name.
   */
  public FileEditorFixture selectTab(@NotNull final String tabName) {
    tabs.waitTab(tabName, Timeouts.INSTANCE.getSeconds05()).selectTab(tabName);
    return this;
  }

  /**
   * Clicks to the editor's center to get focus to the editor
   */
  public void clickCenter() {
    Editor selectedTextEditor = execute(new GuiQuery<Editor>() {
                                          @Override
                                          protected Editor executeInEDT() throws Throwable {
                                            return myManager.getSelectedTextEditor();
                                          }
                                        }
    );
    assert selectedTextEditor != null;
    JComponent contentComponent = selectedTextEditor.getContentComponent();
    robot.click(contentComponent);
  }

  /**
   * Closes the editor with a given tab name.
   */
  public FileEditorFixture closeTab(@NotNull final String tabName) {
    tabs.closeTab(tabName);
    return this;
  }

  public Boolean hasTab(@NotNull final String tabName) {
    return tabs.hasTab(tabName);
  }
}
