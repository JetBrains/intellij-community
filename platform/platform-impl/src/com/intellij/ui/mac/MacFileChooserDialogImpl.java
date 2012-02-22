/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ui.mac;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserUtil;
import com.intellij.openapi.fileChooser.PathChooserDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.IdeMenuBar;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.Consumer;
import com.sun.jna.Callback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author spleaner
 */
@SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
public class MacFileChooserDialogImpl implements PathChooserDialog {
  private static final int OK = 1;

  private static String ourLastPath = null;
  private static FileChooserDescriptor myChooserDescriptor;
  private static List<String> myResultPaths = null;
  private static Consumer<List<String>> myCallback = null;
  private Project myProject;

  private static final Callback SHOULD_ENABLE_URL = new Callback() {
    @SuppressWarnings("UnusedDeclaration")
    public boolean callback(ID self, String selector, ID panel, ID url) {
      return true;
    }
  };

  private static final Callback SHOULD_SHOW_FILENAME_CALLBACK = new Callback() {
    @SuppressWarnings("UnusedDeclaration")
    public boolean callback(ID self, String selector, ID panel, ID filename) {
      if (filename == null || filename.intValue() == 0) return false;
      final String fileName = Foundation.toStringViaUTF8(filename);
      if (fileName == null) return false;
      final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(fileName);
      return virtualFile == null || (virtualFile.isDirectory() || myChooserDescriptor.isFileSelectable(virtualFile));
    }
  };

  private static final Callback IS_VALID_FILENAME_CALLBACK = new Callback() {
    @SuppressWarnings("UnusedDeclaration")
    public boolean callback(ID self, String selector, ID panel, ID filename) {
      if (filename == null || filename.intValue() == 0) return false;
      final String fileName = Foundation.toStringViaUTF8(filename);
      if (fileName == null) return false;
      final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(fileName);
      return virtualFile == null || (!virtualFile.isDirectory() || myChooserDescriptor.isFileSelectable(virtualFile));
    }
  };

  private static final Callback OPEN_PANEL_DID_END = new Callback() {
    @SuppressWarnings("UnusedDeclaration")
    public void callback(ID self, String selector, ID openPanelDidEnd, ID returnCode, ID contextInfo) {
      processResult(returnCode, openPanelDidEnd);

      try {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            IdeMenuBar bar = getMenuBar();
            if (bar != null) {
              bar.enableUpdates();
            }
          }
        });
        if (myResultPaths != null) {
          final List<String> paths = myResultPaths;
          final Consumer<List<String>> callback = myCallback;
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              callback.consume(paths);
            }
          });
        }
      }
      finally {
        myResultPaths = null;
        myCallback = null;
      }

      Foundation.cfRelease(self);
    }
  };

  private static final Callback MAIN_THREAD_RUNNABLE = new Callback() {
    @SuppressWarnings("UnusedDeclaration")
    public void callback(ID self, String selector, ID toSelect) {
      final ID nsOpenPanel = Foundation.getObjcClass("NSOpenPanel");
      final ID chooser = invoke(nsOpenPanel, "openPanel");

      invoke(chooser, "setPrompt:", Foundation.nsString("Choose"));
      invoke(chooser, "setCanChooseFiles:", myChooserDescriptor.isChooseFiles() || myChooserDescriptor.isChooseJars());
      invoke(chooser, "setCanChooseDirectories:", myChooserDescriptor.isChooseFolders());
      invoke(chooser, "setAllowsMultipleSelection:", myChooserDescriptor.isChooseMultiple());
      invoke(chooser, "setTreatsFilePackagesAsDirectories:", myChooserDescriptor.isChooseFolders());

      if (Foundation.isClassRespondsToSelector(nsOpenPanel, Foundation.createSelector("setCanCreateDirectories:"))) {
        invoke(chooser, "setCanCreateDirectories:", true);
      }
      else if (Foundation.isClassRespondsToSelector(nsOpenPanel, Foundation.createSelector("_setIncludeNewFolderButton:"))) {
        invoke(chooser, "_setIncludeNewFolderButton:", true);
      }

      final Boolean showHidden = myChooserDescriptor.getUserData(PathChooserDialog.NATIVE_MAC_CHOOSER_SHOW_HIDDEN_FILES);
      if (Boolean.TRUE.equals(showHidden) || Registry.is("ide.mac.filechooser.showhidden.files")) {
        if (Foundation.isClassRespondsToSelector(nsOpenPanel, Foundation.createSelector("setShowsHiddenFiles:"))) {
          invoke(chooser, "setShowsHiddenFiles:", true);
        }
      }

      invoke(chooser, "setDelegate:", self);

      ID directory = null;
      ID file = null;
      final String toSelectPath = toSelect == null || toSelect.intValue() == 0 ? null : Foundation.toStringViaUTF8(toSelect);
      final VirtualFile toSelectFile = toSelectPath == null ? null : LocalFileSystem.getInstance().findFileByPath(toSelectPath);
      if (toSelectFile != null) {
        if (toSelectFile.isDirectory()) {
          directory = toSelect;
        }
        else {
          directory = Foundation.nsString(toSelectFile.getParent().getPath());
          file = Foundation.nsString(toSelectFile.getName());
        }
      }

      ID types = null;
      if (!myChooserDescriptor.isChooseFiles() && myChooserDescriptor.isChooseJars()) {
        types = invoke("NSArray", "arrayWithObject:", Foundation.nsString("jar"));
      }

      final Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
      if (activeWindow != null) {
        String activeWindowTitle = null;
        if (activeWindow instanceof Frame) {
          activeWindowTitle = ((Frame)activeWindow).getTitle();
        }
        else if (activeWindow instanceof JDialog) {
          activeWindowTitle = ((JDialog)activeWindow).getTitle();
        }

        final ID focusedWindow = MacUtil.findWindowForTitle(activeWindowTitle);
        if (focusedWindow != null) {
          invoke(chooser, "beginSheetForDirectory:file:types:modalForWindow:modalDelegate:didEndSelector:contextInfo:",
                 directory, file, types, focusedWindow, self, Foundation.createSelector("openPanelDidEnd:returnCode:contextInfo:"), null);
        }
      }

      Foundation.cfRelease(directory, file, types);
    }
  };

  private static void processResult(final ID result, final ID panel) {
    final List<String> resultFiles = new ArrayList<String>();
    if (result != null && OK == result.intValue()) {
      ID fileNamesArray = invoke(panel, "filenames");
      ID enumerator = invoke(fileNamesArray, "objectEnumerator");

      while (true) {
        final ID filename = invoke(enumerator, "nextObject");
        if (filename == null || 0 == filename.intValue()) break;

        String s = Foundation.toStringViaUTF8(filename);
        if (s != null) {
          resultFiles.add(s);
        }
      }

      myResultPaths = resultFiles;
    }
  }

  static {
    final ID delegate = Foundation.allocateObjcClassPair(Foundation.getObjcClass("NSObject"), "NSOpenPanelDelegate_");
    if (!Foundation.addMethod(delegate, Foundation.createSelector("panel:shouldShowFilename:"), SHOULD_SHOW_FILENAME_CALLBACK, "B*")) {
      throw new RuntimeException("Unable to add method to objective-c delegate class!");
    }
    if (!Foundation.addMethod(delegate, Foundation.createSelector("panel:isValidFilename:"), IS_VALID_FILENAME_CALLBACK, "B*")) {
      throw new RuntimeException("Unable to add method to objective-c delegate class!");
    }
    if (!Foundation.addMethod(delegate, Foundation.createSelector("showOpenPanel:"), MAIN_THREAD_RUNNABLE, "v*")) {
      throw new RuntimeException("Unable to add method to objective-c delegate class!");
    }
    if (!Foundation.addMethod(delegate, Foundation.createSelector("openPanelDidEnd:returnCode:contextInfo:"), OPEN_PANEL_DID_END, "v*i")) {
      throw new RuntimeException("Unable to add method to objective-c delegate class!");
    }
    if (!Foundation.addMethod(delegate, Foundation.createSelector("panel:shouldEnableURL:"), SHOULD_ENABLE_URL, "B@@")) {
      throw new RuntimeException("Unable to add method to objective-c delegate class!");
    }
    Foundation.registerObjcClassPair(delegate);
  }

  public MacFileChooserDialogImpl(@NotNull FileChooserDescriptor chooserDescriptor, Project project) {
    myChooserDescriptor = chooserDescriptor;
    myProject = project;
  }

  @Override
  public void choose(@Nullable final String toSelect, @NotNull final Consumer<List<String>> callback) {
    assert myCallback == null : "Current native file chooser should finish before next usage!";
    myCallback = callback;

    final String selectPath = FileChooserUtil.getPathToSelect(myChooserDescriptor, myProject, toSelect, ourLastPath);

    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        showNativeChooserAsSheet(selectPath);
      }
    });
  }

  @Nullable
  private static IdeMenuBar getMenuBar() {
    Window cur = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();

    while (cur != null) {
      if (cur instanceof JFrame) {
        JMenuBar menuBar = ((JFrame)cur).getJMenuBar();
        if (menuBar instanceof IdeMenuBar) {
          return (IdeMenuBar)menuBar;
        }
      }
      cur = cur.getOwner();
    }
    return null;
  }

  private static void showNativeChooserAsSheet(@Nullable String toSelect) {
    IdeMenuBar bar = getMenuBar();
    if (bar != null) {
      bar.disableUpdates();
    }

    final ID autoReleasePool = createAutoReleasePool();
    try {
      final ID delegate = invoke(Foundation.getObjcClass("NSOpenPanelDelegate_"), "new");
      Foundation.cfRetain(delegate);

      final ID select = toSelect == null ? null : Foundation.nsString(toSelect);

      invoke(delegate, "performSelectorOnMainThread:withObject:waitUntilDone:", Foundation.createSelector("showOpenPanel:"), select, false);
    }
    finally {
      invoke(autoReleasePool, "release");
    }
  }

  private static ID createAutoReleasePool() {
    return invoke("NSAutoreleasePool", "new");
  }

  private static ID invoke(@NotNull final String className, @NotNull final String selector, Object... args) {
    return invoke(Foundation.getObjcClass(className), selector, args);
  }

  private static ID invoke(@NotNull final ID id, @NotNull final String selector, Object... args) {
    return Foundation.invoke(id, Foundation.createSelector(selector), args);
  }
}
