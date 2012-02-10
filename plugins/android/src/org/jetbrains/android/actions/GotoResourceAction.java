/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android.actions;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */

public class GotoResourceAction extends AnAction {
  public GotoResourceAction() {
    super(AndroidBundle.message("navigate.to.android.resource.action"));
  }

  public void update(final AnActionEvent e) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        Presentation presentation = e.getPresentation();
        DataContext dataContext = e.getDataContext();
        Project project = getProject(dataContext);
        if (project != null) {
          Editor editor = getEditor(dataContext);
          if (editor != null) {
            PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
            if (file != null) {
              AndroidFacet facet = AndroidFacet.getInstance(file);
              if (facet != null) {
                int offset = TargetElementUtilBase.adjustOffset(editor.getDocument(), editor.getCaretModel().getOffset());
                PsiElement element = file.findElementAt(offset);
                boolean enabled = false;
                if (element != null) {
                  PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class);
                  if (field != null) {
                    if (isRJavaField(file, field)) {
                      enabled = true;
                    }
                  }
                  while (!enabled && element != null) {
                    element = PsiTreeUtil.getParentOfType(element, PsiReferenceExpression.class);
                    if (element != null) {
                      PsiElement targetElement = ((PsiReferenceExpression)element).resolve();
                      if (targetElement instanceof PsiField) {
                        if (isRJavaField(targetElement.getContainingFile(), (PsiField)targetElement)) {
                          enabled = true;
                        }
                      }
                    }
                  }
                }
                presentation.setEnabled(enabled || AndroidResourceUtil.isInResourceSubdirectory(file, null));
                return;
              }
            }
          }
          else if (PlatformDataKeys.FILE_EDITOR.getData(dataContext) != null) {
            PsiFile psiFile = getPsiFileByVirtualFileKey(project, dataContext);
            if (psiFile != null) {
              boolean enabled = AndroidFacet.getInstance(psiFile) != null && AndroidResourceUtil.isInResourceSubdirectory(psiFile, null);
              presentation.setEnabled(enabled);
              return;
            }
          }
        }
        presentation.setEnabled(false);
      }
    });
  }

  @Nullable
  private static PsiFile getPsiFileByVirtualFileKey(@NotNull Project project, @NotNull DataContext context) {
    VirtualFile virtualFile = PlatformDataKeys.VIRTUAL_FILE.getData(context);
    if (virtualFile != null) {
      return PsiManager.getInstance(project).findFile(virtualFile);
    }
    return null;
  }

  @Nullable
  private static Project getProject(DataContext context) {
    return PlatformDataKeys.PROJECT.getData(context);
  }

  @Nullable
  private static Editor getEditor(DataContext context) {
    return PlatformDataKeys.EDITOR.getData(context);
  }

  @NotNull
  public static PsiElement[] findTargets(DataContext context) {
    Editor editor = getEditor(context);
    Project project = getProject(context);
    assert project != null;
    PsiFile file = null;
    if (editor != null) {
      file = PsiUtilBase.getPsiFileInEditor(editor, project);
      assert file != null;
      int offset = TargetElementUtilBase.adjustOffset(editor.getDocument(), editor.getCaretModel().getOffset());
      PsiElement element = file.findElementAt(offset);
      XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class, false);
      if (tag != null) {
        XmlAttributeValue attributeValue = PsiTreeUtil.getParentOfType(element, XmlAttributeValue.class, false);
        PsiField[] fields = null;
        if (attributeValue != null) {
          fields = AndroidResourceUtil.findIdFields(attributeValue);
        }
        if (fields == null || fields.length == 0) {
          fields = AndroidResourceUtil.findResourceFieldsForValueResource(tag, false);
        }
        if (fields.length > 0) return fields;
      }
      PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class, false);
      if (field != null) {
        final List<PsiElement> elements = AndroidResourceUtil.findResourcesByField(field);
        return elements.toArray(new PsiElement[elements.size()]);
      }
      while (element != null) {
        element = PsiTreeUtil.getParentOfType(element, PsiReferenceExpression.class);
        if (element != null) {
          PsiElement targetElement = ((PsiReferenceExpression)element).resolve();
          if (targetElement instanceof PsiField) {
            final List<PsiElement> elements = AndroidResourceUtil.findResourcesByField((PsiField)targetElement);
            return elements.toArray(new PsiElement[elements.size()]);
          }
        }
      }
    }
    if (file == null) {
      file = getPsiFileByVirtualFileKey(project, context);
      assert file != null;
    }
    return AndroidResourceUtil.findResourceFieldsForFileResource(file, false);
  }

  @Nullable
  private static RelativePoint getRelativePointToShowPopup(DataContext context) {
    Editor editor = getEditor(context);
    if (editor != null) {
      LogicalPosition position = editor.getCaretModel().getLogicalPosition();
      if (position != null) {
        Point point = editor.logicalPositionToXY(position);
        return new RelativePoint(editor.getComponent(), point);
      }
    }
    return null;
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext context = e.getDataContext();
    PsiElement[] targets = findTargets(context);
    if (targets.length > 0) {
      AndroidUtils.navigateTo(targets, getRelativePointToShowPopup(context));
    }
  }

  private static boolean isRJavaField(@NotNull PsiFile file, @NotNull PsiField field) {
    PsiClass c = field.getContainingClass();
    if (c == null) return false;
    c = c.getContainingClass();
    if (c != null && AndroidUtils.R_CLASS_NAME.equals(c.getName())) {
      AndroidFacet facet = AndroidFacet.getInstance(file);
      if (facet != null) {
        return AndroidResourceUtil.isRJavaFile(facet, file);
      }
    }
    return false;
  }
}
