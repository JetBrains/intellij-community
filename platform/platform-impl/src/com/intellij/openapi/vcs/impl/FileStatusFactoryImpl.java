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
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusFactory;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;


public class FileStatusFactoryImpl implements FileStatusFactory {
  private final List<FileStatus> myStatuses = new ArrayList<FileStatus>();

  public FileStatus createFileStatus(String id, String description, Color color) {
    FileStatusImpl result = new FileStatusImpl(id, ColorKey.createColorKey("FILESTATUS_" + id, color), description);
    myStatuses.add(result);
    return result;
  }

  public FileStatus[] getAllFileStatuses() {
    return myStatuses.toArray(new FileStatus[myStatuses.size()]);
  }
}