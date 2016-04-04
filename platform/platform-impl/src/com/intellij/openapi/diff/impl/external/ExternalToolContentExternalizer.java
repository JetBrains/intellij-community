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
package com.intellij.openapi.diff.impl.external;

import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.MergeRequest;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;

/**
* @author Konstantin Bulenkov
*/
class ExternalToolContentExternalizer implements ContentExternalizer {
  private final DiffRequest myRequest;
  private final int myIndex;
  @NonNls public static final String STD_PREFIX = "IJDiff";

  public ExternalToolContentExternalizer(DiffRequest request, int index) {
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
    final DiffContent content = getContent();
    byte[] bytes = myRequest instanceof MergeRequest ? content.getDocument().getText().getBytes() : content.getBytes();
    FileUtil.writeToFile(tempFile, bytes);
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
