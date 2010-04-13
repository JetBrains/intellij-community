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

  static {
    final ID delegateClass = Foundation.registerObjcClass(Foundation.getClass("NSObject"), "NSOpenPanelDelegate_");
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

    final ID autoReleasePool = createAutoReleasePool();

    try {
      final ID chooser = invoke("NSOpenPanel", "openPanel");

      invoke(chooser, "autorelease");
      invoke(chooser, "setPrompt:", Foundation.cfString("Choose"));
      invoke(chooser, "setCanChooseFiles:", myChooserDescriptor.isChooseFiles());
      invoke(chooser, "setCanChooseDirectories:", myChooserDescriptor.isChooseFolders());
      invoke(chooser, "setAllowsMultipleSelection:", myChooserDescriptor.isChooseMultiple());
      if (Foundation.isClassRespondsToSelector(Foundation.getClass("NSOpenPanel"), Foundation.createSelector("_setIncludeNewFolderButton:"))) {
        invoke(chooser, "_setIncludeNewFolderButton:", true);
      }

      final ID delegate = invoke(Foundation.getClass("NSOpenPanelDelegate_"), "new");
      invoke(chooser, "setDelegate:", delegate);
      
      final Object directory = toSelect != null ? toSelect.isDirectory() ? Foundation.cfString(toSelect.getPath()) : null : null;
      final Object file = toSelect != null ? !toSelect.isDirectory() ? Foundation.cfString(toSelect.getPath()) : null : null;
      final ID result = invoke(chooser, "runModalForDirectory:file:", directory, file);

      if (result != null && OK == result.intValue()) {
        ID fileNamesArray = invoke(chooser, "filenames");
        ID enumerator = invoke(fileNamesArray, "objectEnumerator");

        final ArrayList<VirtualFile> fileNames = new ArrayList<VirtualFile>();
        while (true) {
          final ID filename = invoke(enumerator, "nextObject");
          if (0 == filename.intValue()) break;

          String s = Foundation.toStringViaUTF8(filename);
          if (s != null) {
            VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(s);
            if (virtualFile != null && virtualFile.isValid()) fileNames.add(virtualFile);
          }
        }

        return fileNames.toArray(new VirtualFile[fileNames.size()]);
      }
    }
    finally {
      invoke(autoReleasePool, "drain");
      myFileChooserActive = false;
    }

    return new VirtualFile[0];
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
