/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.external;

import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

class ExtCompareFiles extends BaseExternalTool {
  public static final BaseExternalTool INSTANCE = new ExtCompareFiles();
  private ExtCompareFiles() {
    super(DiffManagerImpl.ENABLE_FILES, DiffManagerImpl.FILES_TOOL);
  }

  public boolean canShow(DiffRequest request) {
    DiffContent[] contents = request.getContents();
    for (int i = 0; i < contents.length; i++) {
      DiffContent content = contents[i];
      VirtualFile file = getLocalFile(content.getFile());
      if (file != null && file.isDirectory()) return false;
      if (canExternalizeAsFile(file)) continue;
      if (DiffUtil.isWritable(content)) return false;
    }
    return super.canShow(request);
  }

  protected BaseExternalTool.ContentExternalizer externalize(final DiffRequest request, final int index) {
    VirtualFile file = getLocalFile(request.getContents()[index].getFile());
    if (canExternalizeAsFile(file)) return LocalFileExternalizer.tryCreate(file);
    return new MyContentExternalizer(request, index);
  }

  private boolean canExternalizeAsFile(VirtualFile file) {
    if (file == null || file.isDirectory()) return false;
    FileType fileType = file.getFileType();
    if (fileType.isBinary() && fileType != FileTypes.UNKNOWN) return false;
    return true;
  }

  private static class MyContentExternalizer implements BaseExternalTool.ContentExternalizer {
    private final DiffRequest myRequest;
    private final int myIndex;
    @NonNls public static final String STD_PREFIX = "IJDiff";

    public MyContentExternalizer(DiffRequest request, int index) {
      myRequest = request;
      myIndex = index;
    }

    public File getContentFile() throws IOException {
      String extension = chooseExtension();
      String name = chooseName();
      if (name.length() <= 3) name = "___" + name;
      File tempFile;
      try {
        tempFile = FileUtil.createTempFile(name, extension);
      }
      catch (IOException e) {
        tempFile = FileUtil.createTempFile(STD_PREFIX, extension);
      }
      FileOutputStream stream = null;
      try {
        stream = new FileOutputStream(tempFile);
        stream.write(getContent().getBytes());
      } finally {
        if (stream != null) stream.close();
      }
      return tempFile;
    }

    private String chooseName() {
      String title = myRequest.getContentTitles()[myIndex];
      char[] chars = title.toCharArray();
      for (int i = 0; i < chars.length; i++) {
        char aChar = chars[i];
        if (!Character.isLetterOrDigit(aChar)) chars[i] = '_';
      }
      return new String(chars);
    }

    private String chooseExtension() {
      DiffContent content = getContent();
      VirtualFile contentFile = content.getFile();
      String extension;
      if (contentFile != null) {
        extension = "." + contentFile.getExtension();
      }
      else {
        FileType contentType = content.getContentType();
        if (contentType == null) contentType = DiffUtil.chooseContentTypes(myRequest.getContents())[myIndex];
        extension = contentType != null ?  "." + contentType.getDefaultExtension() : null;
      }
      return extension;
    }

    private DiffContent getContent() {
      return myRequest.getContents()[myIndex];
    }
  }
}
