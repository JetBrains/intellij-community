// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.testAssistant.vfs.TestDataGroupVirtualFile;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.jetbrains.idea.devkit.testAssistant.TestDataUtil.getFileByPath;

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
      fileNames = new ArrayList<>();
      fileNames.addAll(TestDataGuessByExistingFilesUtil.collectTestDataByExistingFiles(method));
      fileNames.addAll(TestDataGuessByTestDiscoveryUtil.collectTestDataByExistingFiles(method));
    }
    return fileNames == null ? Collections.emptyList() : fileNames;
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
    Collections.sort(filePaths, String.CASE_INSENSITIVE_ORDER);

    List<TestDataNavigationElement> elementsToDisplay = new ArrayList<>();
    List<TestDataNavigationElement> nonExistingElementsToDisplay = new ArrayList<>();
    Set<VirtualFile> files = new HashSet<>();
    for (String p : filePaths) {
      VirtualFile f = getFileByPath(p);
      if (f == null) {
        if (nonExistingElementsToDisplay.isEmpty()) {
          nonExistingElementsToDisplay.add(TestDataNavigationElementFactory.createForCreateMissingFilesOption(filePaths));
        }
        nonExistingElementsToDisplay.add(TestDataNavigationElementFactory.createForFile(project, p));
      } else {
        files.add(f);
      }
    }
    consumeElementsToDisplay(project, files, elementsToDisplay::add);
    elementsToDisplay.addAll(nonExistingElementsToDisplay);

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
    JBPopupFactory.getInstance()
      .createListPopupBuilder(list)
      .setItemChoosenCallback(() -> {
        TestDataNavigationElement selectedElement = list.getSelectedValue();
        if (selectedElement != null) {
          selectedElement.performAction(project);
        }
      }).setTitle("Test Data").createPopup().show(point);
  }

  private static void consumeElementsToDisplay(@NotNull Project project,
                                               @NotNull Collection<VirtualFile> files,
                                               @NotNull Consumer<TestDataNavigationElement> consumer) {

    Set<VirtualFile> usedPaths = new HashSet<>();
    for (VirtualFile file1 : files) {
      if (usedPaths.contains(file1)) {
        continue;
      }

      boolean groupFound = false;
      for (VirtualFile file2 : files) {
        if (usedPaths.contains(file2) || file2.equals(file1)) {
          continue;
        }

        TestDataGroupVirtualFile group = TestDataUtil.getTestDataGroup(file1, file2);
        if (group == null) {
          continue;
        }

        groupFound = true;
        consumer.accept(TestDataNavigationElementFactory.createForGroup(project, group));
        usedPaths.add(file1);
        usedPaths.add(file2);
        break;
      }

      if (!groupFound) {
        consumer.accept(TestDataNavigationElementFactory.createForFile(project, file1.getPath()));
        usedPaths.add(file1);
      }
    }
  }
}
