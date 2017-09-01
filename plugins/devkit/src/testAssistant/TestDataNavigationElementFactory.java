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

import com.google.common.collect.ImmutableList;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;

import javax.swing.*;
import java.util.*;

public class TestDataNavigationElementFactory {
  private static final int CREATE_MISSING_FILES_WITHOUT_CONFIRMATION_LIMIT = 3;

  private TestDataNavigationElementFactory() {
  }

  @NotNull
  public static TestDataNavigationElement createForFile(@NotNull Project project, @NotNull String path) {
    return new TestDataFileNavigationElement(project, path);
  }

  @NotNull
  public static TestDataNavigationElement createForGroup(@NotNull Project project, @NotNull TestDataGroupVirtualFile group) {
    return new TestDataGroupNavigationElement(project, group);
  }

  @NotNull
  public static TestDataNavigationElement createForCreateMissingFilesOption(@NotNull List<String> filePaths) {
    return new CreateMissingTestDataFilesNavigationElement(filePaths);
  }


  private static class CreateMissingTestDataFilesNavigationElement implements TestDataNavigationElement {
    private final List<String> myFilePaths;

    private CreateMissingTestDataFilesNavigationElement(List<String> filePaths) {
      myFilePaths = filePaths;
    }

    @Override
    public void performAction(@NotNull Project project) {
      Set<String> filePathsToCreate = new HashSet<>();
      for (String path : myFilePaths) {
        if (LocalFileSystem.getInstance().refreshAndFindFileByPath(path) == null) {
          filePathsToCreate.add(path);
        }
      }

      if (filePathsToCreate.size() > CREATE_MISSING_FILES_WITHOUT_CONFIRMATION_LIMIT) {
        int code = Messages.showOkCancelDialog(
          project, DevKitBundle.message("testdata.confirm.create.missing.files.dialog.message", StringUtil.join(filePathsToCreate, "\n")),
          DevKitBundle.message("testdata.create.missing.files"), Messages.getQuestionIcon());
        if (code != Messages.OK) {
          return;
        }
      }

      filePathsToCreate.forEach(path -> {
        VirtualFile file = TestDataUtil.createFileByName(project, path);
        new OpenFileDescriptor(project, file).navigate(true);
      });
    }

    @Override
    public Icon getIcon() {
      return null;
    }

    @NotNull
    @Override
    public List<Pair<String, SimpleTextAttributes>> getTitleFragments() {
      return Collections.singletonList(new Pair<>(
        DevKitBundle.message("testdata.create.missing.files"), SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES));
    }
  }

  private static class TestDataGroupNavigationElement implements TestDataNavigationElement {
    private final Project myProject;
    private final TestDataGroupVirtualFile myGroup;

    private TestDataGroupNavigationElement(Project project, TestDataGroupVirtualFile group) {
      myProject = project;
      myGroup = group;
    }

    @Override
    public void performAction(@NotNull Project project) {
      new OpenFileDescriptor(project, myGroup).navigate(true);
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return AllIcons.Nodes.TestSourceFolder;
    }

    @NotNull
    @Override
    public List<Pair<String, SimpleTextAttributes>> getTitleFragments() {
      VirtualFile beforeFile = myGroup.getBeforeFile();
      VirtualFile afterFile = myGroup.getAfterFile();
      String beforeName = beforeFile.getName();
      String afterName = afterFile.getName();

      List<Pair<String, SimpleTextAttributes>> result = new ArrayList<>();
      result.add(new Pair<>("<" + beforeName + ", " + afterName + "> (", SimpleTextAttributes.REGULAR_ATTRIBUTES));

      Pair<String, String> beforeRelativePath = TestDataUtil.getModuleOrProjectRelativeParentPath(myProject, beforeFile);
      Pair<String, String> afterRelativePath = TestDataUtil.getModuleOrProjectRelativeParentPath(myProject, afterFile);
      if (beforeRelativePath != null && afterRelativePath != null) {
        String beforeBase = beforeRelativePath.getFirst();
        String afterBase = afterRelativePath.getFirst();
        String beforeBaseRelativePath = beforeRelativePath.getSecond();
        String afterBaseRelativePath = afterRelativePath.getSecond();

        if (beforeBase.equals(afterBase)) {
          result.add(new Pair<>(beforeBase, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES));
          if (beforeBaseRelativePath.equals(afterBaseRelativePath)) { // same dir
            result.add(new Pair<>("/" + beforeBaseRelativePath + "/)", SimpleTextAttributes.REGULAR_ATTRIBUTES));
          }
          else { // same base but different dirs
            result.add(new Pair<>("/", SimpleTextAttributes.REGULAR_ATTRIBUTES));
            String commonPrefix = StringUtil.commonPrefix(beforeBaseRelativePath, afterBaseRelativePath);
            if (!commonPrefix.isEmpty()) {
              result.add(new Pair<>(commonPrefix, SimpleTextAttributes.REGULAR_ATTRIBUTES));
            }
            String beforeUniqueSuffix = beforeBaseRelativePath.substring(commonPrefix.length());
            String afterUniqueSuffix = afterBaseRelativePath.substring(commonPrefix.length());
            result.add(new Pair<>("<" + beforeUniqueSuffix + "/, " + afterUniqueSuffix + "/>", SimpleTextAttributes.REGULAR_ATTRIBUTES));
          }
        }
        else { // different bases
          result.add(new Pair<>(beforeBase, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES));
          result.add(new Pair<>("/" + beforeBaseRelativePath + "/, ", SimpleTextAttributes.REGULAR_ATTRIBUTES));
          result.add(new Pair<>(afterBase, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES));
          result.add(new Pair<>("/" + afterBaseRelativePath + "/", SimpleTextAttributes.REGULAR_ATTRIBUTES));
        }
      }

      return result;
    }
  }

  private static class TestDataFileNavigationElement implements TestDataNavigationElement {
    private final Project myProject;
    private final String myPath;

    private TestDataFileNavigationElement(Project project, String path) {
      myProject = project;
      myPath = path;
    }

    @Override
    public void performAction(@NotNull Project project) {
      TestDataUtil.openOrAskToCreateFile(project, myPath);
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return TestDataUtil.getIcon(myPath);
    }

    @NotNull
    @Override
    public List<Pair<String, SimpleTextAttributes>> getTitleFragments() {
      VirtualFile file = TestDataUtil.getFileByPath(myPath);
      if (file == null) {
        return Collections.singletonList(new Pair<>(
          String.format("%s (%s)", PathUtil.getFileName(myPath), PathUtil.getParentPath(myPath)),
          SimpleTextAttributes.GRAYED_ATTRIBUTES));
      }

      Pair<String, String> relativePath = TestDataUtil.getModuleOrProjectRelativeParentPath(myProject, file);
      if (relativePath == null) {
        // cannot calculate module/project relative path, use absolute path
        return Collections.singletonList(new Pair<>(
          String.format("%s (%s)", file.getName(), PathUtil.getParentPath(myPath) + "/"),
          SimpleTextAttributes.REGULAR_ATTRIBUTES));
      }

      return ImmutableList.of(new Pair<>(file.getName() + " (", SimpleTextAttributes.REGULAR_ATTRIBUTES),
                              new Pair<>(relativePath.getFirst(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES),
                              new Pair<>("/" + relativePath.getSecond() + "/)", SimpleTextAttributes.REGULAR_ATTRIBUTES));
    }
  }
}
