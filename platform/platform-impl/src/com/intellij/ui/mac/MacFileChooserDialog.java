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
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.sun.jna.Callback;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * @author spleaner
 */
public class MacFileChooserDialog implements FileChooserDialog {
  private static final int OK = 1;

  private static FileChooserDescriptor myChooserDescriptor;
  private static boolean myFileChooserActive = false;

  private static final Callback SHOULD_SHOW_FILENAME_CALLBACK = new Callback() {
      public boolean callback(Pointer self, String selector, Pointer panel, Pointer filename) {
        final String fileName = Foundation.toStringViaUTF8(filename);
        final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(fileName);
        return virtualFile != null && (virtualFile.isDirectory() || getDescriptor().isFileSelectable(virtualFile));
      }
    };

  private static final Callback IS_VALID_FILENAME_CALLBACK = new Callback() {
      public boolean callback(Pointer self, String selector, Pointer panel, Pointer filename) {
        final String fileName = Foundation.toStringViaUTF8(filename);
        final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(fileName);
        return virtualFile != null && (!virtualFile.isDirectory() || getDescriptor().isFileSelectable(virtualFile));
      }
    };

  static {
    final Pointer delegateClass = Foundation.registerObjcClass(Foundation.getClass("NSObject"), "NSOpenPanelDelegate_");
    if (!Foundation.addMethod(delegateClass, Foundation.createSelector("panel:shouldShowFilename:"), SHOULD_SHOW_FILENAME_CALLBACK, "B@:*"))
      throw new RuntimeException("Unable to add method to objective-c delegate class!");
    if (!Foundation.addMethod(delegateClass, Foundation.createSelector("panel:isValidFilename:"), IS_VALID_FILENAME_CALLBACK, "B@:*"))
      throw new RuntimeException("Unable to add method to objective-c delegate class!");
    Foundation.registerObjcClassPair(delegateClass);
  }

  public MacFileChooserDialog(FileChooserDescriptor chooserDescriptor) {
    setDescriptor(chooserDescriptor);
  }

  private static void setDescriptor(@Nullable final FileChooserDescriptor descriptor) {
    myChooserDescriptor = descriptor;
  }

  private static FileChooserDescriptor getDescriptor() {
    return myChooserDescriptor;
  }

  @NotNull
  public VirtualFile[] choose(@Nullable VirtualFile toSelect, @Nullable Project project) {
    assert !myFileChooserActive: "Current native file chooser should finish before next usage!";

    myFileChooserActive = true;

    final Pointer autoReleasePool = createAutoReleasePool();

    try {
      final Pointer chooser = invoke("NSOpenPanel", "openPanel");

      invoke(chooser, "setPrompt:", Foundation.cfString("Choose"));
      invoke(chooser, "autorelease");
      invoke(chooser, "setCanChooseFiles:", myChooserDescriptor.isChooseFiles());
      invoke(chooser, "setCanChooseDirectories:", myChooserDescriptor.isChooseFolders());
      invoke(chooser, "setAllowsMultipleSelection:", myChooserDescriptor.isChooseMultiple());
      if (Foundation.isClassRespondsToSelector(Foundation.getClass("NSOpenPanel"), Foundation.createSelector("_setIncludeNewFolderButton:"))) {
        invoke(chooser, "_setIncludeNewFolderButton:", true);
      }

      final Pointer delegate = invoke(Foundation.getClass("NSOpenPanelDelegate_"), "new");
      invoke(chooser, "setDelegate:", delegate);
      
      final Object directory = toSelect != null ? toSelect.isDirectory() ? Foundation.cfString(toSelect.getPath()).toLong() : null : null;
      final Object file = toSelect != null ? !toSelect.isDirectory() ? Foundation.cfString(toSelect.getPath()).toLong() : null : null;
      final ID result = invoke(chooser, "runModalForDirectory:file:", directory, file);
      if (result != null && OK == result.toInt()) {
        ID fileNamesArray = invoke(chooser, "filenames");
        ID enumerator = invoke(fileNamesArray, "objectEnumerator");

        final ArrayList<VirtualFile> fileNames = new ArrayList<VirtualFile>();
        while (true) {
          final ID filename = invoke(enumerator, "nextObject");
          if (0 == filename.toInt()) break;

          String s = Foundation.toStringViaUTF8(filename);
          if (s != null) {
            VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(s);
            if (virtualFile != null && virtualFile.isValid()) fileNames.add(virtualFile);
          }
        }

        return fileNames.toArray(new VirtualFile[fileNames.size()]);
      }

      invoke(delegate, "release");
    }
    finally {
      invoke(autoReleasePool, "drain");
      myFileChooserActive = false;
    }

    return new VirtualFile[0];
  }

  private static Pointer createAutoReleasePool() {
    return invoke("NSAutoreleasePool", "new");
  }

  private static ID invoke(@NotNull final String className, @NotNull final String selector, Object... args) {
    return invoke(Foundation.getClass(className), selector, args);
  }

  private static ID invoke(@NotNull final Pointer id, @NotNull final String selector, Object... args) {
    return Foundation.invoke(id, Foundation.createSelector(selector), args);
  }
}
