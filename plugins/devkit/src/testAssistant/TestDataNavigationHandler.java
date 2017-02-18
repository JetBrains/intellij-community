/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
* @author yole
*/
public class TestDataNavigationHandler implements GutterIconNavigationHandler<PsiMethod> {
  public void navigate(MouseEvent e, final PsiMethod elt) {
    List<String> fileNames = getFileNames(elt);

    if (fileNames == null || fileNames.isEmpty()) {
      return;
    }
    navigate(new RelativePoint(e), fileNames, elt.getProject());
  }

  @Nullable
  static List<String> getFileNames(PsiMethod method) {
    List<String> fileNames = null;
    String testDataPath = TestDataLineMarkerProvider.getTestDataBasePath(method.getContainingClass());
    if (testDataPath != null) {
      fileNames = new TestDataReferenceCollector(testDataPath, method.getName().substring(4)).collectTestDataReferences(method);
    }

    if (fileNames == null || fileNames.isEmpty()) {
      fileNames = TestDataGuessByExistingFilesUtil.collectTestDataByExistingFiles(method);
    }
    return fileNames;
  }

  public static void navigate(@NotNull final RelativePoint point,
                              @NotNull List<String> testDataFiles, 
                              final Project project) {
    if (testDataFiles.size() == 1) {
      openFileByIndex(project, testDataFiles, 0);
    }
    else if (testDataFiles.size() > 1) {
      TestDataGroupVirtualFile groupFile = getTestDataGroup(testDataFiles);
      if (groupFile != null) {
        new OpenFileDescriptor(project, groupFile).navigate(true);
      }
      else {
        showNavigationPopup(project, testDataFiles, point);
      }
    }
  }

  @Nullable
  private static TestDataGroupVirtualFile getTestDataGroup(List<String> fileNames) {
    if (fileNames.size() != 2) {
      return null;
    }
    VirtualFile file1 = LocalFileSystem.getInstance().refreshAndFindFileByPath(fileNames.get(0));
    VirtualFile file2 = LocalFileSystem.getInstance().refreshAndFindFileByPath(fileNames.get(1));
    if (file1 == null || file2 == null) {
      return null;
    }
    final int commonPrefixLength = StringUtil.commonPrefixLength(file1.getName(), file2.getName());
    if (file1.getName().substring(commonPrefixLength).toLowerCase().contains("after")) {
      return new TestDataGroupVirtualFile(file2, file1);
    }
    if (file2.getName().substring(commonPrefixLength).toLowerCase().contains("after")) {
      return new TestDataGroupVirtualFile(file1, file2);
    }
    return null;
  }

  private static void showNavigationPopup(final Project project, final List<String> fileNames, final RelativePoint point) {
    List<String> listPaths = new ArrayList<>(fileNames);
    final String CREATE_MISSING_OPTION = "Create Missing Files";
    if (fileNames.size() == 2) {
      VirtualFile file1 = LocalFileSystem.getInstance().refreshAndFindFileByPath(fileNames.get(0));
      VirtualFile file2 = LocalFileSystem.getInstance().refreshAndFindFileByPath(fileNames.get(1));
      if (file1 == null || file2 == null) {
        listPaths.add(CREATE_MISSING_OPTION);
      }
    }
    final JList list = new JBList(ArrayUtil.toStringArray(listPaths));
    list.setCellRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
        String path = (String)value;
        String fileName = PathUtil.getFileName(path);
        if (!fileName.equals(CREATE_MISSING_OPTION)) {
          final FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
          setIcon(fileType.getIcon());
        }
        append(String.format("%s (%s)", fileName, PathUtil.getParentPath(path)));
      }
    });
    PopupChooserBuilder builder = new PopupChooserBuilder(list);
    builder.setItemChoosenCallback(() -> {
      final int[] indices = list.getSelectedIndices();
      if (ArrayUtil.indexOf(indices, fileNames.size()) >= 0) {
        createMissingFiles(project, fileNames);
      }
      else {
        for (int index : indices) {
          openFileByIndex(project, fileNames, index);
        }
      }
    }).createPopup().show(point);
  }

  private static void createMissingFiles(Project project, List<String> fileNames) {
    for (String name : fileNames) {
      if (LocalFileSystem.getInstance().refreshAndFindFileByPath(name) == null) {
        createFileByName(project, name);
      }
    }
    final TestDataGroupVirtualFile testDataGroup = getTestDataGroup(fileNames);
    if (testDataGroup != null) {
      new OpenFileDescriptor(project, testDataGroup).navigate(true);
    }
  }

  private static void openFileByIndex(final Project project, final List<String> fileNames, final int index) {
    final String path = fileNames.get(index);
    final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
    if (file != null) {
      new OpenFileDescriptor(project, file).navigate(true);
    }
    else {
      int rc = Messages.showYesNoDialog(project, "The referenced testdata file " + path + " does not exist. Would you like to create it?",
                                        "Create Testdata File", Messages.getQuestionIcon());
      if (rc == Messages.YES) {
        VirtualFile vFile = createFileByName(project, path);
        new OpenFileDescriptor(project, vFile).navigate(true);
      }
    }
  }

  private static VirtualFile createFileByName(final Project project, final String path) {
    return ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      public VirtualFile compute() {
        try {
          final File file = new File(path);
          final VirtualFile parent = VfsUtil.createDirectories(file.getParent());
          return parent.createChildData(this, file.getName());
        }
        catch (IOException e) {
          Messages.showErrorDialog(project, e.getMessage(), "Create Testdata File");
          return null;
        }
      }
    });
  }
}
