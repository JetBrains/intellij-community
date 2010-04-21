/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.sun.jna.Callback;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author spleaner
 */
public class MacFileChooserDialog implements FileChooserDialog {
  private static final int OK = 1;

  private static JDialog myFakeDialog;
  private static List<VirtualFile> myResultFiles;

  private static FileChooserDescriptor myChooserDescriptor;
  private static boolean myFileChooserActive = false;

  private Project myProject;

  private static final Callback SHOULD_SHOW_FILENAME_CALLBACK = new Callback() {
      public boolean callback(ID self, String selector, ID panel, ID filename) {
        final String fileName = Foundation.toStringViaUTF8(filename);
        final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(fileName);
        return virtualFile != null && (virtualFile.isDirectory() || getDescriptor().isFileSelectable(virtualFile));
      }
    };

  private static final Callback IS_VALID_FILENAME_CALLBACK = new Callback() {
      public boolean callback(ID self, String selector, ID panel, ID filename) {
        final String fileName = Foundation.toStringViaUTF8(filename);
        final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(fileName);
        return virtualFile != null && (!virtualFile.isDirectory() || getDescriptor().isFileSelectable(virtualFile));
      }
    };

  private static final Callback MAIN_THREAD_RUNNABLE = new Callback() {
    public void callback(ID self, String selector, ID toSelect) {
      final ID chooser = invoke("NSOpenPanel", "openPanel");

      invoke(chooser, "setPrompt:", Foundation.cfString("Choose"));
      invoke(chooser, "setCanChooseFiles:", myChooserDescriptor.isChooseFiles());
      invoke(chooser, "setCanChooseDirectories:", myChooserDescriptor.isChooseFolders());
      invoke(chooser, "setAllowsMultipleSelection:", myChooserDescriptor.isChooseMultiple());
      if (Foundation.isClassRespondsToSelector(Foundation.getClass("NSOpenPanel"), Foundation.createSelector("_setIncludeNewFolderButton:"))) {
        invoke(chooser, "_setIncludeNewFolderButton:", true);
      }

      invoke(chooser, "setDelegate:", self);

      final String toSelectPath = toSelect.intValue() == 0 ? null : Foundation.toStringViaUTF8(toSelect);
      final VirtualFile toSelectFile = toSelectPath == null ? null : LocalFileSystem.getInstance().findFileByPath(toSelectPath);
      final ID directory = toSelectFile == null ? null : toSelectFile.isDirectory() ? toSelect : null;
      final ID file = toSelectFile == null ? null : !toSelectFile.isDirectory() ? toSelect : null;
      final ID result = invoke(chooser, "runModalForDirectory:file:", directory, file);

      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          if (myFakeDialog != null) {
            myFakeDialog.dispose();
            myFakeDialog = null;
          }
        }
      });

      final List<VirtualFile> resultFiles = new ArrayList<VirtualFile>();
      if (result != null && OK == result.intValue()) {
        ID fileNamesArray = invoke(chooser, "filenames");
        ID enumerator = invoke(fileNamesArray, "objectEnumerator");

        while (true) {
          final ID filename = invoke(enumerator, "nextObject");
          if (0 == filename.intValue()) break;

          String s = Foundation.toStringViaUTF8(filename);
          if (s != null) {
            VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(s);
            if (virtualFile != null && virtualFile.isValid()) resultFiles.add(virtualFile);
          }
        }

        myResultFiles = resultFiles;
      }
    }
  };

  static {
    final ID delegateClass = Foundation.registerObjcClass(Foundation.getClass("NSObject"), "NSOpenPanelDelegate_");
    if (!Foundation.addMethod(delegateClass, Foundation.createSelector("panel:shouldShowFilename:"), SHOULD_SHOW_FILENAME_CALLBACK, "B@:*"))
      throw new RuntimeException("Unable to add method to objective-c delegate class!");
    if (!Foundation.addMethod(delegateClass, Foundation.createSelector("panel:isValidFilename:"), IS_VALID_FILENAME_CALLBACK, "B@:*"))
      throw new RuntimeException("Unable to add method to objective-c delegate class!");
    if (!Foundation.addMethod(delegateClass, Foundation.createSelector("showOpenPanel:"), MAIN_THREAD_RUNNABLE, "v@:*"))
      throw new RuntimeException("Unable to add method to objective-c delegate class!");
    Foundation.registerObjcClassPair(delegateClass);
  }

  public MacFileChooserDialog(final FileChooserDescriptor chooserDescriptor, final Project project) {
    setDescriptor(chooserDescriptor);
    myProject = project;
  }

  public MacFileChooserDialog(final FileChooserDescriptor chooserDescriptor, final Component component) {
    setDescriptor(chooserDescriptor);
  }

  private static void setDescriptor(@Nullable final FileChooserDescriptor descriptor) {
    myChooserDescriptor = descriptor;
  }

  private static FileChooserDescriptor getDescriptor() {
    return myChooserDescriptor;
  }

  private static void showNativeChooser(@Nullable VirtualFile toSelect) {
    final ID autoReleasePool = createAutoReleasePool();

    try {
      final ID delegate = invoke(Foundation.getClass("NSOpenPanelDelegate_"), "new");

      final Pointer select = toSelect == null ? null : Foundation.cfString(toSelect.getPath());
      Foundation.cfRetain(delegate);

      invoke(delegate, "performSelectorOnMainThread:withObject:waitUntilDone:", Foundation.createSelector("showOpenPanel:"), select, false);
    }
    finally {
      invoke(autoReleasePool, "release");
    }
  }

  @NotNull
  public VirtualFile[] choose(@Nullable final VirtualFile toSelect, @Nullable Project project) {
    assert !myFileChooserActive: "Current native file chooser should finish before next usage!";

    myFileChooserActive = true;

    final VirtualFile[] selectFile = new VirtualFile[] {null};
    if (toSelect == null) {
      if (project != null && project.getBaseDir() != null) {
        selectFile[0] = project.getBaseDir();
      }
    } else {
      selectFile[0] = toSelect.isValid() ? toSelect : null;
    }

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        showNativeChooser(selectFile[0]);
      }
    });

    myFakeDialog = new JDialog(project != null ? WindowManager.getInstance().getFrame(project) : myProject != null ? WindowManager.getInstance().getFrame(myProject) : null);
    myFakeDialog.setModal(true);
    myFakeDialog.setUndecorated(true);
    myFakeDialog.getRootPane().putClientProperty( "Window.shadow", Boolean.FALSE );

    myFakeDialog.setBounds(0, 0, 0, 0);
    myFakeDialog.setVisible(true);

    try {
      if (myResultFiles == null) {
        return new VirtualFile[0];
      } else {
        return myResultFiles.toArray(new VirtualFile[myResultFiles.size()]);
      }
    }
    finally {
      myFileChooserActive = false;
      myResultFiles = null;
    }
  }

  private static ID createAutoReleasePool() {
    return invoke("NSAutoreleasePool", "new");
  }

  private static ID invoke(@NotNull final String className, @NotNull final String selector, Object... args) {
    return invoke(Foundation.getClass(className), selector, args);
  }

  private static ID invoke(@NotNull final ID id, @NotNull final String selector, Object... args) {
    return Foundation.invoke(id, Foundation.createSelector(selector), args);
  }
}
