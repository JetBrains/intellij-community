/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.options;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SchemeImportUtil {
  @Nullable
  public static VirtualFile selectImportSource(@NotNull final String[] sourceExtensions,
                                               @NotNull Component parent,
                                               @Nullable VirtualFile preselect,
                                               @Nullable String description) {
    final Set<String> extensions = new HashSet<>(Arrays.asList(sourceExtensions));
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, canSelectJarFile(sourceExtensions), false, false, false) {
      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        return
          (file.isDirectory() || extensions.contains(file.getExtension())) &&
          (showHiddenFiles || !FileElement.isFileHidden(file));
      }

      @Override
      public boolean isFileSelectable(VirtualFile file) {
        return !file.isDirectory() && extensions.contains(file.getExtension());
      }
    };
    if (description != null) {
      descriptor.setDescription(description);
    }
    FileChooserDialog fileChooser = FileChooserFactory.getInstance()
      .createFileChooser(descriptor, null, parent);
    final VirtualFile[] preselectFiles;
    if (preselect != null) {
      preselectFiles = new VirtualFile[1];
      preselectFiles[0] = preselect;
    }
    else {
      preselectFiles = VirtualFile.EMPTY_ARRAY;
    }
    final VirtualFile[] virtualFiles = fileChooser.choose(null, preselectFiles); 
    if (virtualFiles.length != 1) return null;
    virtualFiles[0].refresh(false, false);
    return virtualFiles[0];
  }

  private static boolean canSelectJarFile(@NotNull String[] sourceExtensions) {
    for (String ext : sourceExtensions) {
      if ("jar".equals(ext)) return true;
    }
    return false;
  }

  @NotNull
  public static Element loadSchemeDom(@NotNull VirtualFile file) throws SchemeImportException {
    InputStream inputStream = null;
    try {
      inputStream = file.getInputStream();
      final Document document = JDOMUtil.loadDocument(inputStream);
      final Element root = document.getRootElement();
      inputStream.close();
      return root;
    }
    catch (IOException | JDOMException e) {
      throw new SchemeImportException();
    }
    finally {
      if (inputStream != null) {
        try {
          inputStream.close();
        }
        catch (IOException e) {
          // ignore
        }
      }
    }
  }

}
