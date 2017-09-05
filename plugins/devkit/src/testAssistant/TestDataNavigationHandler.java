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
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class TestDataNavigationHandler implements GutterIconNavigationHandler<PsiMethod> {
  @Override
  public void navigate(MouseEvent e, PsiMethod elt) {
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

  public static void navigate(@NotNull RelativePoint point,
                              @NotNull List<String> testDataFiles,
                              Project project) {
    if (testDataFiles.size() == 1) {
      TestDataUtil.openOrAskToCreateFile(project, testDataFiles.get(0));
    }
    else if (testDataFiles.size() > 1) {
      TestDataGroupVirtualFile groupFile = TestDataUtil.getTestDataGroup(testDataFiles);
      if (groupFile != null) {
        new OpenFileDescriptor(project, groupFile).navigate(true);
      }
      else {
        showNavigationPopup(project, testDataFiles, point);
      }
    }
  }

  /**
   * Shows navigation popup with list of testdata files and (optionally) "Create missing files" option.
   * @param project project.
   * @param filePaths paths of testdata files with "/" path separator. This List can be changed.
   * @param point point where the popup will be shown.
   */
  private static void showNavigationPopup(Project project, List<String> filePaths, RelativePoint point) {
    List<TestDataNavigationElement> elementsToDisplay = getElementsToDisplay(project, filePaths);

    // if at least one file doesn't exist add "Create missing files" element
    for (String path : filePaths) {
      if (LocalFileSystem.getInstance().refreshAndFindFileByPath(path) == null) {
        elementsToDisplay.add(TestDataNavigationElementFactory.createForCreateMissingFilesOption(filePaths));
        break;
      }
    }

    JList<TestDataNavigationElement> list = new JBList<>(elementsToDisplay);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(new ColoredListCellRenderer<TestDataNavigationElement>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, TestDataNavigationElement element, int index,
                                           boolean selected, boolean hasFocus) {
        element.getTitleFragments().forEach(pair -> append(pair.getFirst(), pair.getSecond()));
        setIcon(element.getIcon());
      }
    });

    PopupChooserBuilder builder = new PopupChooserBuilder(list);
    builder.setItemChoosenCallback(() -> {
      TestDataNavigationElement selectedElement = list.getSelectedValue();
      if (selectedElement != null) {
        selectedElement.performAction(project);
      }
    }).createPopup().show(point);
  }

  private static List<TestDataNavigationElement> getElementsToDisplay(Project project, List<String> filePaths) {
    ContainerUtil.removeDuplicates(filePaths);

    filePaths.sort((path1, path2) -> {
      String name1 = PathUtil.getFileName(path1);
      String name2 = PathUtil.getFileName(path2);
      name1 = StringUtil.trimStart(name1, "before");
      name2 = StringUtil.trimStart(name2, "before");
      name1 = StringUtil.trimStart(name1, "after");
      name2 = StringUtil.trimStart(name2, "after");
      return name1.compareToIgnoreCase(name2);
    });

    List<TestDataNavigationElement> result = new ArrayList<>();
    for (ListIterator<String> iterator = filePaths.listIterator(); iterator.hasNext(); ) {
      String path = iterator.next();

      // check if there's a testdata group
      if (iterator.hasNext()) {
        String nextPath = iterator.next();
        TestDataGroupVirtualFile group = TestDataUtil.getTestDataGroup(path, nextPath);
        if (group != null) {
          result.add(TestDataNavigationElementFactory.createForGroup(project, group));
          continue;
        }
        else {
          iterator.previous();
        }
      }

      result.add(TestDataNavigationElementFactory.createForFile(project, path));
    }

    return result;
  }
}
