package com.intellij.testAssistant;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Icons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class TestDataLineMarkerProvider implements LineMarkerProvider {
  public LineMarkerInfo getLineMarkerInfo(PsiElement element) {
    if (!(element instanceof PsiMethod)) {
      return null;
    }
    final PsiMethod method = (PsiMethod)element;
    String name = method.getName();
    if (!name.startsWith("test")) {
      return null;
    }
    String testDataPath = getTestDataBasePath(method.getContainingClass());
    if (testDataPath != null) {
      List<String> fileNames = new TestDataReferenceCollector(testDataPath, name.substring(4)).collectTestDataReferences(method);
      if (fileNames.size() > 0) {
        return new LineMarkerInfo<PsiMethod>(method, method.getTextOffset(), Icons.TEST_SOURCE_FOLDER, Pass.UPDATE_ALL, null,
                                             new TestDataNavigationHandler(fileNames));
      }
    }
    return null;
  }

  public void collectSlowLineMarkers(List<PsiElement> elements, Collection<LineMarkerInfo> result) {
  }

  @Nullable
  private static String getTestDataBasePath(PsiClass psiClass) {
    final PsiAnnotation annotation = AnnotationUtil.findAnnotationInHierarchy(psiClass, Collections.singleton("com.intellij.testFramework.TestDataPath"));
    if (annotation != null) {
      final PsiAnnotationMemberValue value = annotation.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
      if (value instanceof PsiExpression) {
        final PsiConstantEvaluationHelper evaluationHelper = JavaPsiFacade.getInstance(value.getProject()).getConstantEvaluationHelper();
        final Object constantValue = evaluationHelper.computeConstantExpression(value, false);
        if (constantValue instanceof String) {
          String path = (String) constantValue;
          if (path.indexOf("$CONTENT_ROOT") >= 0) {
            final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(psiClass.getProject()).getFileIndex();
            final VirtualFile contentRoot = fileIndex.getContentRootForFile(psiClass.getContainingFile().getVirtualFile());
            if (contentRoot == null) return null;
            path = path.replace("$CONTENT_ROOT", contentRoot.getPath());
          }
          return path;
        }
      }
    }
    return null;
  }

  private static class TestDataNavigationHandler implements GutterIconNavigationHandler<PsiMethod> {
    private List<String> myFileNames;

    public TestDataNavigationHandler(List<String> fileNames) {
      myFileNames = fileNames;
    }

    public void navigate(MouseEvent e, final PsiMethod elt) {
      if (myFileNames.size() == 1) {
        openFileByIndex(elt.getProject(), 0);
      }
      else {
        TestDataGroupVirtualFile groupFile = getTestDataGroup();
        if (groupFile != null) {
          new OpenFileDescriptor(elt.getProject(), groupFile).navigate(true);
        }
        else {
          showNavigationPopup(elt.getProject(), e);
        }
      }
    }

    @Nullable
    private TestDataGroupVirtualFile getTestDataGroup() {
      if (myFileNames.size() != 2) {
        return null;
      }
      VirtualFile file1 = LocalFileSystem.getInstance().refreshAndFindFileByPath(myFileNames.get(0));
      VirtualFile file2 = LocalFileSystem.getInstance().refreshAndFindFileByPath(myFileNames.get(1));
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

    private void showNavigationPopup(final Project project, MouseEvent e) {
      List<String> shortNames = new ArrayList<String>();
      for (String fileName : myFileNames) {
        shortNames.add(new File(fileName).getName());
      }
      final String CREATE_MISSING_OPTION = "Create Missing Files";
      if (myFileNames.size() == 2) {
        shortNames.add(CREATE_MISSING_OPTION);
      }
      final JList list = new JList(shortNames.toArray(new String[shortNames.size()]));
      list.setCellRenderer(new ColoredListCellRenderer() {
        @Override
        protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          String fileName = (String)value;
          if (!fileName.equals(CREATE_MISSING_OPTION)) {
            final FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
            setIcon(fileType.getIcon());
          }
          append(fileName);
        }
      });
      PopupChooserBuilder builder = new PopupChooserBuilder(list);
      builder.setItemChoosenCallback(new Runnable() {
        public void run() {
          final int[] indices = list.getSelectedIndices();
          if (ArrayUtil.indexOf(indices, myFileNames.size()) >= 0) {
            createMissingFiles(project);
          }
          else {
            for (int index : indices) {
              openFileByIndex(project, index);
            }
          }
        }
      }).createPopup().show(new RelativePoint(e));
    }

    private void createMissingFiles(Project project) {
      for (String name : myFileNames) {
        if (LocalFileSystem.getInstance().refreshAndFindFileByPath(name) == null) {
          createFileByName(project, name);
        }
      }
      final TestDataGroupVirtualFile testDataGroup = getTestDataGroup();
      if (testDataGroup != null) {
        new OpenFileDescriptor(project, testDataGroup).navigate(true);
      }
    }

    private void openFileByIndex(final Project project, final int index) {
      final String path = myFileNames.get(index);
      final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
      if (file != null) {
        new OpenFileDescriptor(project, file).navigate(true);
      }
      else {
        int rc = Messages.showYesNoDialog(project, "The referenced testdata file " + path + " does not exist. Would you like to create it?",
                                          "Create Testdata File", Messages.getQuestionIcon());
        if (rc == 0) {
          VirtualFile vFile = createFileByName(project, path);
          new OpenFileDescriptor(project, vFile).navigate(true);
        }
      }
    }

    private VirtualFile createFileByName(final Project project, final String path) {
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
}
