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
package org.jetbrains.android;

import com.intellij.history.LocalHistory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenameJavaVariableProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.RenameXmlAttributeProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper;
import org.jetbrains.android.dom.wrappers.ValueResourceElementWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.jetbrains.android.util.AndroidBundle.message;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidRenameResourceProcessor extends RenamePsiElementProcessor {
  // for tests
  public static volatile boolean ASK = true;

  public boolean canProcessElement(@NotNull final PsiElement element) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        final PsiElement element1 = LazyValueResourceElementWrapper.computeLazyElement(element);
        if (element1 == null) {
          return false;
        }

        if (element1 instanceof PsiFile) {
          return AndroidFacet.getInstance(element1) != null && AndroidResourceUtil.isInResourceSubdirectory((PsiFile)element1, null);
        }
        else if (element1 instanceof PsiField) {
          PsiField field = (PsiField)element1;
          if (AndroidResourceUtil.isResourceField(field)) {
            return AndroidResourceUtil.findResourcesByField(field).size() > 0;
          }
        }
        else if (element1 instanceof XmlAttributeValue) {
          LocalResourceManager manager = LocalResourceManager.getInstance(element1);
          if (manager != null) {
            if (AndroidResourceUtil.isIdDeclaration((XmlAttributeValue)element1)) {
              return true;
            }
            // then it is value resource
            XmlTag tag = PsiTreeUtil.getParentOfType(element1, XmlTag.class);
            return tag != null &&
                   DomManager.getDomManager(tag.getProject()).getDomElement(tag) instanceof ResourceElement &&
                   manager.getValueResourceType(tag) != null;
          }
        }
        return false;
      }
    });
  }

  @Override
  public void prepareRenaming(PsiElement element, String newName, Map<PsiElement, String> allRenames) {
    final PsiElement element1 = LazyValueResourceElementWrapper.computeLazyElement(element);
    if (element1 == null) {
      return;
    }

    // todo: support renaming alternative value resources

    AndroidFacet facet = AndroidFacet.getInstance(element1);
    assert facet != null;
    if (element1 instanceof PsiFile) {
      prepareResourceFileRenaming((PsiFile)element1, newName, allRenames, facet);
    }
    else if (element1 instanceof XmlAttributeValue) {
      XmlAttributeValue value = (XmlAttributeValue)element1;
      if (AndroidResourceUtil.isIdDeclaration(value)) {
        prepareIdRenaming(value, newName, allRenames, facet);
      }
      else {
        prepareValueResourceRenaming(element1, newName, allRenames, facet);
      }
    }
    else if (element1 instanceof PsiField) {
      prepareResourceFieldRenaming((PsiField)element1, newName, allRenames);
    }
  }

  private static void prepareIdRenaming(XmlAttributeValue value, String newName, Map<PsiElement, String> allRenames, AndroidFacet facet) {
    LocalResourceManager manager = facet.getLocalResourceManager();
    allRenames.remove(value);
    String id = AndroidResourceUtil.getResourceNameByReferenceText(value.getValue());
    assert id != null;
    List<PsiElement> idDeclarations = manager.findIdDeclarations(id);
    if (idDeclarations != null) {
      for (PsiElement idDeclaration : idDeclarations) {
        allRenames.put(new ValueResourceElementWrapper((XmlAttributeValue)idDeclaration), newName);
      }
    }
    String name = AndroidResourceUtil.getResourceNameByReferenceText(newName);
    if (name != null) {
      for (PsiField resField : AndroidResourceUtil.findIdFields(value)) {
        allRenames.put(resField, AndroidResourceUtil.getFieldNameByResourceName(name));
      }
    }
  }

  @Nullable
  private static String getResourceName(Project project, String newFieldName, String oldResourceName) {
    if (newFieldName.indexOf('_') < 0) return newFieldName;
    if (oldResourceName.indexOf('_') < 0 && oldResourceName.indexOf('.') >= 0) {
      String suggestion = newFieldName.replace('_', '.');
      newFieldName = Messages.showInputDialog(project, AndroidBundle.message("rename.resource.dialog.text", oldResourceName),
                                              RefactoringBundle.message("rename.title"), Messages.getQuestionIcon(), suggestion, null);
    }
    return newFieldName;
  }

  private static void prepareResourceFieldRenaming(PsiField field, String newName, Map<PsiElement, String> allRenames) {
    new RenameJavaVariableProcessor().prepareRenaming(field, newName, allRenames);
    List<PsiElement> resources = AndroidResourceUtil.findResourcesByField(field);
    PsiElement res = resources.get(0);
    String resName = res instanceof XmlAttributeValue ? ((XmlAttributeValue)res).getValue() : ((PsiFile)res).getName();
    final String newResName = getResourceName(field.getProject(), newName, resName);

    for (PsiElement resource : resources) {
      if (resource instanceof PsiFile) {
        PsiFile file = (PsiFile)resource;
        String extension = FileUtil.getExtension(file.getName());
        allRenames.put(resource, newResName + '.' + extension);
      }
      else if (resource instanceof XmlAttributeValue) {
        XmlAttributeValue value = (XmlAttributeValue)resource;
        final String s = AndroidResourceUtil.isIdDeclaration(value)
                         ? AndroidResourceUtil.NEW_ID_PREFIX + newResName
                         : newResName;
        allRenames.put(new ValueResourceElementWrapper(value), s);
      }
    }
  }

  private static void prepareValueResourceRenaming(PsiElement element,
                                                   String newName,
                                                   Map<PsiElement, String> allRenames,
                                                   AndroidFacet facet) {
    ResourceManager manager = facet.getLocalResourceManager();
    XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    assert tag != null;
    String type = manager.getValueResourceType(tag);
    assert type != null;
    Project project = tag.getProject();
    DomElement domElement = DomManager.getDomManager(project).getDomElement(tag);
    assert domElement instanceof ResourceElement;
    String name = ((ResourceElement)domElement).getName().getValue();
    assert name != null;
    List<ResourceElement> resources = manager.findValueResources(type, name);
    for (ResourceElement resource : resources) {
      XmlElement xmlElement = resource.getName().getXmlAttributeValue();
      if (!element.getManager().areElementsEquivalent(element, xmlElement)) {
        allRenames.put(xmlElement, newName);
      }
    }
    PsiField[] resFields = AndroidResourceUtil.findResourceFieldsForValueResource(tag, false);
    for (PsiField resField : resFields) {
      allRenames.put(resField, AndroidResourceUtil.getFieldNameByResourceName(newName));
    }
  }

  private static void prepareResourceFileRenaming(PsiFile file, String newName, Map<PsiElement, String> allRenames, AndroidFacet facet) {
    Project project = file.getProject();
    ResourceManager manager = facet.getLocalResourceManager();
    String type = manager.getFileResourceType(file);
    if (type == null) return;
    String name = file.getName();

    if (AndroidCommonUtils.getResourceName(type, name).equals(AndroidCommonUtils.getResourceName(type, newName))) {
      return;
    }

    List<PsiFile> resourceFiles = manager.findResourceFiles(type, AndroidCommonUtils.getResourceName(type, name), true, false);
    List<PsiFile> alternativeResources = new ArrayList<PsiFile>();
    for (PsiFile resourceFile : resourceFiles) {
      if (!resourceFile.getManager().areElementsEquivalent(file, resourceFile) && resourceFile.getName().equals(name)) {
        alternativeResources.add(resourceFile);
      }
    }
    if (alternativeResources.size() > 0) {
      int r = 0;
      if (ASK) {
        r = Messages.showYesNoDialog(project, message("rename.alternate.resources.question"), message("rename.dialog.title"),
                                     Messages.getQuestionIcon());
      }
      if (r == 0) {
        for (PsiFile candidate : alternativeResources) {
          allRenames.put(candidate, newName);
        }
      }
      else {
        return;
      }
    }
    PsiField[] resFields = AndroidResourceUtil.findResourceFieldsForFileResource(file, false);
    for (PsiField resField : resFields) {
      String newFieldName = AndroidCommonUtils.getResourceName(type, newName);
      allRenames.put(resField, AndroidResourceUtil.getFieldNameByResourceName(newFieldName));
    }
  }

  @Override
  public void renameElement(PsiElement element, final String newName, UsageInfo[] usages, RefactoringElementListener listener)
    throws IncorrectOperationException {
    if (element instanceof PsiField) {
      new RenameJavaVariableProcessor().renameElement(element, newName, usages, listener);
    }
    else {
      if (element instanceof PsiNamedElement) {
        super.renameElement(element, newName, usages, listener);

        if (element instanceof PsiFile) {
          VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
          if (virtualFile != null && !LocalHistory.getInstance().isUnderControl(virtualFile)) {
            DocumentReference ref = DocumentReferenceManager.getInstance().create(virtualFile);
            UndoManager.getInstance(element.getProject()).nonundoableActionPerformed(ref, false);
          }
        }
      }
      else if (element instanceof XmlAttributeValue) {
        new RenameXmlAttributeProcessor().renameElement(element, newName, usages, listener);
      }
    }
  }
}
