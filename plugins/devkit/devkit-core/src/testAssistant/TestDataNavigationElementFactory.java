// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.FontUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.testAssistant.vfs.TestDataGroupVirtualFile;

import javax.swing.*;
import java.util.*;

public class TestDataNavigationElementFactory {
  private static final int CREATE_MISSING_FILES_WITHOUT_CONFIRMATION_LIMIT = 3;

  private TestDataNavigationElementFactory() {
  }

  @NotNull
  public static TestDataNavigationElement createForNonExistingFile(@NotNull Project project, @NotNull TestDataFile path) {
    return new NonExistingTestDataFileNavigationElement(project, path);
  }

  @NotNull
  public static TestDataNavigationElement createForFile(@NotNull Project project, @NotNull TestDataFile file) {
    return new TestDataFileNavigationElement(project, file);
  }

  @NotNull
  public static TestDataNavigationElement createForGroup(@NotNull Project project, @NotNull TestDataGroupVirtualFile group) {
    return new TestDataGroupNavigationElement(project, group);
  }

  @NotNull
  public static TestDataNavigationElement createForCreateMissingFilesOption(@NotNull List<TestDataFile> filePaths) {
    return new CreateMissingTestDataFilesNavigationElement(filePaths);
  }

  private static class CreateMissingTestDataFilesNavigationElement implements TestDataNavigationElement {
    private final List<TestDataFile> myFilePaths;

    private CreateMissingTestDataFilesNavigationElement(List<TestDataFile> filePaths) {
      myFilePaths = filePaths;
    }

    @Override
    public void performAction(@NotNull Project project) {
      Set<String> filePathsToCreate = new HashSet<>();
      for (TestDataFile file : myFilePaths) {
        VirtualFile vFile = file.getVirtualFile();
        if (vFile == null || !vFile.isValid()) {
          filePathsToCreate.add(file.getPath());
        }
      }

      if (filePathsToCreate.size() > CREATE_MISSING_FILES_WITHOUT_CONFIRMATION_LIMIT) {
        List<String> displayPaths = new ArrayList<>();
        for (String path : filePathsToCreate) {
          displayPaths.add(TestDataUtil.getHtmlDisplayPathForMissingFile(project, path));
        }

        displayPaths.sort(String.CASE_INSENSITIVE_ORDER);
        String filePathsDisplayStr = StringUtil.join(displayPaths, "\n");

        int code = Messages.showOkCancelDialog(
          project, DevKitBundle.message("testdata.confirm.create.missing.files.dialog.message", filePathsDisplayStr),
          DevKitBundle.message("testdata.create.missing.files"), Messages.getQuestionIcon());
        if (code != Messages.OK) {
          return;
        }
      }

      filePathsToCreate.forEach(path -> {
        VirtualFile file = TestDataUtil.createFileByPath(project, path);
        PsiNavigationSupport.getInstance().createNavigatable(project, file, -1).navigate(true);
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
      PsiNavigationSupport.getInstance().createNavigatable(project, myGroup, -1).navigate(true);
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
      result.add(new Pair<>(TestDataUtil.getGroupDisplayName(beforeName, afterName) + " (", SimpleTextAttributes.REGULAR_ATTRIBUTES));

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

  private static class NonExistingTestDataFileNavigationElement implements TestDataNavigationElement {
    private final Project myProject;
    private final TestDataFile myPath;

    private NonExistingTestDataFileNavigationElement(@NotNull Project project, @NotNull TestDataFile path) {
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
      return FileTypes.UNKNOWN.getIcon();
    }

    @NotNull
    @Override
    public List<Pair<String, SimpleTextAttributes>> getTitleFragments() {
      Pair<String, String> relativePath = TestDataUtil.getRelativePathPairForMissingFile(myProject, myPath.getPath());
      return ContainerUtil.newSmartList(
        new Pair<>(myPath.getName() + FontUtil.spaceAndThinSpace(), SimpleTextAttributes.GRAYED_ATTRIBUTES),
        new Pair<>(relativePath.first == null ? "" : relativePath.first, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES),
        new Pair<>(relativePath.first == null ? "" : "/" + relativePath.second, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      );
    }
  }

  private static class TestDataFileNavigationElement implements TestDataNavigationElement {
    private final Project myProject;
    private final TestDataFile myFile;

    private TestDataFileNavigationElement(@NotNull Project project, @NotNull TestDataFile file) {
      myProject = project;
      myFile = file;
    }

    @Override
    public void performAction(@NotNull Project project) {
      TestDataUtil.openOrAskToCreateFile(project, myFile);
    }

    @Nullable
    @Override
    public Icon getIcon() {
      VirtualFile file = myFile.getVirtualFile();
      assert file != null;
      return file.getFileType().getIcon();
    }

    @NotNull
    @Override
    public List<Pair<String, SimpleTextAttributes>> getTitleFragments() {
      VirtualFile file = myFile.getVirtualFile();
      assert file != null;
      Pair<String, String> relativePath = TestDataUtil.getModuleOrProjectRelativeParentPath(myProject, myFile.getVirtualFile());
      if (relativePath == null) {
        // cannot calculate module/project relative path, use absolute path

        return ContainerUtil.newSmartList(new Pair<>(
          String.format("%s (%s)", myFile.getName(), file.getParent().getPath() + "/"),
          SimpleTextAttributes.REGULAR_ATTRIBUTES));
      }

      return ContainerUtil.newSmartList(new Pair<>(myFile.getName() + FontUtil.spaceAndThinSpace(), SimpleTextAttributes.REGULAR_ATTRIBUTES),
                                        new Pair<>(relativePath.first, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES),
                                        new Pair<>("/" + relativePath.second, SimpleTextAttributes.GRAYED_ATTRIBUTES));
    }
  }
}
