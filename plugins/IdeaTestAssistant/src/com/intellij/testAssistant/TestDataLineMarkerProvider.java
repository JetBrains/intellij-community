package com.intellij.testAssistant;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Icons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.io.File;
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
        List<String> shortNames = new ArrayList<String>();
        for (String fileName : myFileNames) {
          shortNames.add(new File(fileName).getName());
        }
        final JList list = new JList(shortNames.toArray(new String[shortNames.size()]));
        list.setCellRenderer(new ColoredListCellRenderer() {
          @Override
          protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
            String fileName = (String) value;
            final FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
            setIcon(fileType.getIcon());
            append(fileName);
          }
        });
        PopupChooserBuilder builder = new PopupChooserBuilder(list);
        builder.setItemChoosenCallback(new Runnable() {
          public void run() {
            final int[] indices = list.getSelectedIndices();
            for (int index : indices) {
              openFileByIndex(elt.getProject(), index);
            }
          }
        }).createPopup().show(new RelativePoint(e));
      }
    }

    private void openFileByIndex(final Project project, final int index) {
      final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(myFileNames.get(index));
      if (file != null) {
        new OpenFileDescriptor(project, file).navigate(true);
      }
    }
  }
}
