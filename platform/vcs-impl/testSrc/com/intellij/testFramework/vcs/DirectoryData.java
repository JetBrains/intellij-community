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
package com.intellij.testFramework.vcs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;

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
    return myBase.findChild("dirData");
  }

  public void clear() {
    final VirtualFile base = getBase();
    if (base != null) {
      new WriteCommandAction.Simple(null) {
        @Override
        protected void run() throws Throwable {
          base.delete(this);
        }
      }.execute().throwException();
    }
  }

  public void create() throws IOException {
    final Runnable runnable = new Runnable() {
      @Override
      public void run() {
        try {
          final List<VirtualFile> currentLevel = new ArrayList<>();
          final List<VirtualFile> nextLevel = new ArrayList<>();

          try {
            myBase.createChildDirectory(this, "dirData");
          }
          catch (IOException ignored) {
          }
          VirtualFile newBase = getBase();
          assert newBase != null;
          currentLevel.add(newBase);
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
        }
        catch (IOException e) {
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
