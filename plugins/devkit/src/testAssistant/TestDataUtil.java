/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.testAssistant.vfs.TestDataGroupVirtualFile;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class TestDataUtil {
  private static final String TESTDATA_FILE_AFTER_MARKER = "after";
  private static final String TESTDATA_FILE_BEFORE_MARKER = "before";
  public static final String BEFORE_AFTER_DISPLAY_NAME_PART = TESTDATA_FILE_BEFORE_MARKER + "/" + TESTDATA_FILE_AFTER_MARKER;

  private TestDataUtil() {
  }

  @NotNull
  public static String getGroupDisplayName(@NotNull String beforeName, @NotNull String afterName) {
    if (isBeforeAfterPrefixedPair(beforeName, afterName)) {
      return BEFORE_AFTER_DISPLAY_NAME_PART + StringUtil.trimStart(beforeName, TESTDATA_FILE_BEFORE_MARKER);
    }

    String commonPrefix = StringUtil.commonPrefix(beforeName, afterName);
    if (!commonPrefix.isEmpty()) {
      String beforeNameExt = PathUtil.getFileExtension(beforeName);
      String beforeNameWithoutExt = beforeNameExt == null ? beforeName : StringUtil.substringBeforeLast(beforeName, "." + beforeNameExt);
      String beforeNameWithoutCommonPrefixAndExt =
        StringUtil.trimStart(beforeNameWithoutExt,
                             commonPrefix.endsWith(".") ? StringUtil.trimEnd(commonPrefix, ".") : commonPrefix);

      String beforeNameWithoutCommonPrefixAndExtAndBefore =
        StringUtil.trimEnd(beforeNameWithoutCommonPrefixAndExt, TESTDATA_FILE_BEFORE_MARKER);
      if (!StringUtil.containsAlphaCharacters(beforeNameWithoutCommonPrefixAndExtAndBefore)) {
        String result = commonPrefix;
        char lastChar = commonPrefix.charAt(commonPrefix.length() - 1);
        if (Character.isDigit(lastChar) || Character.isLetter(lastChar)) {
          result += "_";
        }
        result += BEFORE_AFTER_DISPLAY_NAME_PART;
        if (beforeNameExt != null) {
          result += "." + beforeNameExt;
        }
        return result;
      }
    }

    return beforeName + " | " + afterName;
  }

  @Nullable
  static TestDataGroupVirtualFile getTestDataGroup(@NotNull List<String> fileNames) {
    if (fileNames.size() != 2) {
      return null;
    }
    return getTestDataGroup(fileNames.get(0), fileNames.get(1));
  }

  @Nullable
  static TestDataGroupVirtualFile getTestDataGroup(@NotNull String fileName1, @NotNull String fileName2) {
    VirtualFile file1 = getFileByPath(fileName1);
    VirtualFile file2 = getFileByPath(fileName2);
    if (file1 == null || file2 == null) {
      return null;
    }
    @NonNls String file1Name = file1.getName();
    @NonNls String file2Name = file2.getName();

    int commonPrefixLength = StringUtil.commonPrefixLength(file1Name, file2Name);
    if (commonPrefixLength == 0) {
      //noinspection ConstantConditions - no NPE
      if (isBeforeAfterPrefixedPair(file1Name, file2Name)) {
        return new TestDataGroupVirtualFile(file1, file2);
      }
      if (isBeforeAfterPrefixedPair(file2Name, file1Name)) {
        return new TestDataGroupVirtualFile(file2, file1);
      }
    }

    if (isAfterSuffixed(file1Name, file2Name, commonPrefixLength)) {
      return new TestDataGroupVirtualFile(file2, file1);
    }
    if (isAfterSuffixed(file2Name, file1Name, commonPrefixLength)) {
      return new TestDataGroupVirtualFile(file1, file2);
    }

    return null;
  }

  private static boolean isBeforeAfterPrefixedPair(@NonNls String name1, @NonNls String name2) {
    //noinspection ConstantConditions - no NPE
    return name1.toLowerCase().startsWith(TESTDATA_FILE_BEFORE_MARKER) && name2.startsWith(TESTDATA_FILE_AFTER_MARKER)
           && StringUtil.substringAfter(name1, TESTDATA_FILE_BEFORE_MARKER)
             .equals(StringUtil.substringAfter(name2, TESTDATA_FILE_AFTER_MARKER));
  }

  private static boolean isAfterSuffixed(@NonNls String nameToCheck, @NonNls String secondName, int commonPrefixLength) {
    String nameToCheckLastPart = nameToCheck.substring(commonPrefixLength).toLowerCase();
    if (!nameToCheckLastPart.contains(TESTDATA_FILE_AFTER_MARKER)) {
      return false;
    }

    String nameToCheckWithoutAfter = nameToCheckLastPart.replace(TESTDATA_FILE_AFTER_MARKER, "");
    String nameToCheckExt = StringUtil.substringAfterLast(nameToCheckWithoutAfter, ".");
    String nameToCheckWithoutAfterAndExt = StringUtil.substringBeforeLast(nameToCheckWithoutAfter, ".");

    String secondNameLastPart = secondName.substring(commonPrefixLength).toLowerCase();
    String secondNameWithoutAfterAndExt = nameToCheckExt == null ? secondNameLastPart : secondNameLastPart.replace(nameToCheckExt, "");

    return !StringUtil.containsAlphaCharacters(nameToCheckWithoutAfterAndExt) &&
           !StringUtil.containsAlphaCharacters(secondNameWithoutAfterAndExt);
  }

  static VirtualFile createFileByName(final Project project, final String path) {
    return ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      public VirtualFile compute() {
        try {
          File file = new File(path);
          VirtualFile parent = VfsUtil.createDirectories(file.getParent());
          return parent.createChildData(this, file.getName());
        }
        catch (IOException e) {
          Messages.showErrorDialog(project, e.getMessage(), DevKitBundle.message("testdata.create.dialog.title"));
          return null;
        }
      }
    });
  }

  static void openOrAskToCreateFile(@NotNull Project project, @NotNull String path) {
    VirtualFile file = getFileByPath(path);
    if (file != null) {
      new OpenFileDescriptor(project, file).navigate(true);
    }
    else {
      int rc = Messages.showYesNoDialog(project, DevKitBundle.message("testdata.file.doesn.not.exist", path),
                                        DevKitBundle.message("testdata.create.dialog.title"), Messages.getQuestionIcon());
      if (rc == Messages.YES) {
        VirtualFile vFile = createFileByName(project, path);
        new OpenFileDescriptor(project, vFile).navigate(true);
      }
    }
  }

  @Nullable
  static Icon getIcon(@NotNull String path) {
    VirtualFile file = getFileByPath(path);
    if (file == null) {
      return null;
    }
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(file);
    return fileType.getIcon();
  }

  @Nullable
  static VirtualFile getFileByPath(String path) {
    return LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
  }

  @Nullable
  static Pair<String, String> getModuleOrProjectRelativeParentPath(Project project, VirtualFile file) {
    VirtualFile parent = file.getParent();
    if (parent == null) {
      // shouldn't happen
      return null;
    }

    Module module = ModuleUtilCore.findModuleForFile(parent, project);
    if (module != null) {
      VirtualFile moduleFile = module.getModuleFile();
      if (moduleFile != null) {
        VirtualFile moduleFileDir = moduleFile.getParent();
        if (moduleFileDir != null) {
          String moduleRelativePath = VfsUtilCore.getRelativePath(parent, moduleFileDir);
          if (moduleRelativePath != null) {
            return new Pair<>(module.getName(), moduleRelativePath);
          }
        }
      }
    }

    VirtualFile projectDir = project.getBaseDir();
    if (projectDir != null) {
      String projectRelativePath = VfsUtilCore.getRelativePath(parent, projectDir);
      if (projectRelativePath != null) {
        return new Pair<>(project.getName(), projectRelativePath);
      }
    }

    return null;
  }
}
