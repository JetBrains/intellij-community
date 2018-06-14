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
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.testAssistant.vfs.TestDataGroupVirtualFile;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.stream.Collectors;

public class TestDataNavigationHandler implements GutterIconNavigationHandler<PsiMethod> {
  @Override
  public void navigate(MouseEvent e, PsiMethod elt) {
    List<String> fileNames = getFileNames(elt);

    if (fileNames.isEmpty()) {
      return;
    }
    navigate(new RelativePoint(e), fileNames, elt.getProject());
  }

  @NotNull
  static List<String> getFileNames(PsiMethod method) {
    return getFileNames(method, true);
  }

  @NotNull
  static List<String> getFileNames(PsiMethod method, boolean collectByExistingFiles) {
    List<String> fileNames = null;
    String testDataPath = TestDataLineMarkerProvider.getTestDataBasePath(method.getContainingClass());
    if (testDataPath != null) {
      fileNames = new TestDataReferenceCollector(testDataPath, method.getName().substring(4))
        .collectTestDataReferences(method, collectByExistingFiles);
    }

    if (collectByExistingFiles && (fileNames == null || fileNames.isEmpty())) {
      fileNames = TestDataGuessByExistingFilesUtil.collectTestDataByExistingFiles(method);
    }
    if (fileNames == null) {
      fileNames = Collections.emptyList();
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
        PsiNavigationSupport.getInstance().createNavigatable(project, groupFile, -1).navigate(true);
      }
      else {
        showNavigationPopup(project, testDataFiles, point);
      }
    }
  }

  @NotNull
  public static List<String> fastGetTestDataPathsByRelativePath(@NotNull String testDataFileRelativePath, PsiMethod method) {
    return getFileNames(method, false).stream()
      .filter(path -> path.endsWith(testDataFileRelativePath.startsWith("/") ? testDataFileRelativePath : "/" + testDataFileRelativePath))
      .distinct()
      .collect(Collectors.toList());
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
    PopupChooserBuilder builder = JBPopupFactory.getInstance().createListPopupBuilder(list);
    builder.setItemChoosenCallback(() -> {
      TestDataNavigationElement selectedElement = list.getSelectedValue();
      if (selectedElement != null) {
        selectedElement.performAction(project);
      }
    }).createPopup().show(point);
  }

  private static List<TestDataNavigationElement> getElementsToDisplay(Project project, List<String> filePaths) {
    ContainerUtil.removeDuplicates(filePaths);
    Collections.sort(filePaths, String.CASE_INSENSITIVE_ORDER);

    List<TestDataNavigationElement> result = new ArrayList<>();
    Set<String> usedPaths = new HashSet<>();
    for (String path1 : filePaths) {
      if (usedPaths.contains(path1)) {
        continue;
      }

      boolean groupFound = false;
      for (String path2 : filePaths) {
        if (usedPaths.contains(path2) || path2.equals(path1)) {
          continue;
        }

        TestDataGroupVirtualFile group = TestDataUtil.getTestDataGroup(path1, path2);
        if (group == null) {
          continue;
        }

        groupFound = true;
        result.add(TestDataNavigationElementFactory.createForGroup(project, group));
        usedPaths.add(path1);
        usedPaths.add(path2);
        break;
      }

      if (!groupFound) {
        result.add(TestDataNavigationElementFactory.createForFile(project, path1));
        usedPaths.add(path1);
      }
    }

    return result;
  }
}
