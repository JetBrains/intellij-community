/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorDropHandler;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * @author yole
 */
public class FileDropHandler implements EditorDropHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.text.FileDropHandler");

  private static final DataFlavor unixUriListFlavor;
  static {
    DataFlavor flavor = null;
    try {
      flavor = new DataFlavor("text/uri-list; class=java.lang.String; charset=Unicode");
    }
    catch (ClassNotFoundException e) {
      LOG.error(e);
    }
    unixUriListFlavor = flavor;
  }

  public boolean canHandleDrop(final DataFlavor[] transferFlavors) {
    for (DataFlavor flavor : transferFlavors) {
      if (flavor.equals(DataFlavor.javaFileListFlavor) || flavor.equals(unixUriListFlavor)) {
        return true;
      }
    }
    return false;
  }

  public void handleDrop(@NotNull final Transferable t, @Nullable final Project project) {
    if (project == null) {
      return;
    }

    try {
      if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        @SuppressWarnings("unchecked")
        final List<File> fileList = (List<File>)t.getTransferData(DataFlavor.javaFileListFlavor);
        if (fileList != null) {
          openFiles(project, fileList);
        }
      }
      else if (t.isDataFlavorSupported(unixUriListFlavor)) {
        final String text = (String)t.getTransferData(unixUriListFlavor);
        if (!StringUtil.isEmptyOrSpaces(text)) {
          final String[] uriList = StringUtil.convertLineSeparators(text).split("\n");
          openFiles(project, uriList);
        }
      }
    }
    catch (Exception ignore) { }
  }

  private static void openFiles(final Project project, final List<File> fileList) {
    final LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    for (File file : fileList) {
      final VirtualFile vFile = fileSystem.refreshAndFindFileByIoFile(file);
      if (vFile != null) {
        new OpenFileDescriptor(project, vFile).navigate(true);
      }
    }
  }

  private static void openFiles(final Project project, final String[] uriList) {
    final LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    for (String uriString : uriList) {
      if (StringUtil.isEmptyOrSpaces(uriString) || uriString.startsWith("#")) {
        continue;
      }

      final URI uri;
      try {
        uri = new URI(uriString);
        if (!LocalFileSystem.PROTOCOL.equals(uri.getScheme())) {
          continue;
        }
      }
      catch (URISyntaxException ignore) {
        continue;
      }

      final VirtualFile vFile = fileSystem.refreshAndFindFileByPath(uri.getPath());
      if (vFile != null) {
        new OpenFileDescriptor(project, vFile).navigate(true);
      }
    }
  }
}
