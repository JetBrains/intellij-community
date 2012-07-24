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
package com.intellij.testFramework.vcs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 7/19/12
 * Time: 7:56 PM
 */
public class DirectoryData {
  private final VirtualFile myBase;
  private IOException myException;
  private final int myLevels;
  private final int myItemsInLevel;
  private final String myFilesExtension;

  public DirectoryData(VirtualFile base) {
    this(base, 2, 2, null);
  }

  public DirectoryData(VirtualFile base, int levels, int items, final String filesExtension) {
    myFilesExtension = filesExtension == null ? ".txt" : filesExtension;
    assert levels <= 10 && items <= 10;
    myBase = base;
    myLevels = levels;
    myItemsInLevel = items;
  }

  public VirtualFile getBase() {
    return myBase;
  }

  public void clear() {
    final File ioFile = new File(myBase.getPath());
    final File[] files = ioFile.listFiles();
    for (File file : files) {
      FileUtil.delete(file);
    }
  }

  public void create() throws IOException {
    final Runnable runnable = new Runnable() {
      @Override
      public void run() {
        try {
          final List<VirtualFile> currentLevel = new ArrayList<VirtualFile>();
          final List<VirtualFile> nextLevel = new ArrayList<VirtualFile>();

          currentLevel.add(myBase);
          for (int i = 0; i < myLevels; i++) {
            for (VirtualFile file : currentLevel) {
              String numberInRow;
              String prefix;
              if (i == 0) {
                prefix = "L";
                numberInRow = "0";
              }
              else {
                prefix = file.getName();
                final int endIndex = prefix.indexOf("N");
                numberInRow = prefix.substring(endIndex + 1);
                prefix = prefix.substring(1, endIndex);
              }
              final boolean last = i == (myLevels - 1);
              if (! last) {
                final String dirPrefix = "D" + prefix;
                for (int j = 0; j < myItemsInLevel; j++) {
                  nextLevel.add(file.createChildDirectory(this, dirPrefix + numberInRow + "N" + j));
                }
              }
              final String filePrefix = "F" + prefix;
              for (int j = 0; j < myItemsInLevel; j++) {
                file.createChildData(this, filePrefix + numberInRow + "N" + j + myFilesExtension);
              }
            }
            currentLevel.clear();
            currentLevel.addAll(nextLevel);
            nextLevel.clear();
          }
        } catch (IOException e) {
          myException = e;
        }
      }
    };
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(runnable);
      }
    });
    if (myException != null) throw myException;
  }
}
