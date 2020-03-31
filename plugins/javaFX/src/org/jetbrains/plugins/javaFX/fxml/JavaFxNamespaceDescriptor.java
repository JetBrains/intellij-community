// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.daemon.Validator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxClassTagDescriptor;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxRootTagDescriptor;

import java.util.ArrayList;

public class JavaFxNamespaceDescriptor implements XmlNSDescriptor, Validator<XmlDocument> {
  private XmlFile myFile;

  @Nullable
  @Override
  public XmlElementDescriptor getElementDescriptor(@NotNull XmlTag tag) {
    final XmlTag parentTag = tag.getParentTag();
    if (parentTag != null) {
      final XmlElementDescriptor descriptor = parentTag.getDescriptor();
      return descriptor != null ? descriptor.getElementDescriptor(tag, parentTag) : null;
    }

    final String name = tag.getName();
    if (FxmlConstants.FX_ROOT.equals(name)) {
      return new JavaFxRootTagDescriptor(tag);
    }
    return new JavaFxClassTagDescriptor(name, tag);
  }

  @Override
  public XmlElementDescriptor @NotNull [] getRootElementsDescriptors(@Nullable XmlDocument document) {
    if (document != null) {
      final Project project = document.getProject();
      final PsiClass paneClass = JavaPsiFacade.getInstance(project).findClass(JavaFxCommonNames.JAVAFX_SCENE_LAYOUT_PANE, GlobalSearchScope.allScope(project));
      if (paneClass != null) {
        final ArrayList<XmlElementDescriptor> result = new ArrayList<>();
        ClassInheritorsSearch.search(paneClass, paneClass.getUseScope(), true, true, false).forEach(psiClass -> {
          result.add(new JavaFxClassTagDescriptor(psiClass.getName(), psiClass));
          return true;
        });
        return result.toArray(XmlElementDescriptor.EMPTY_ARRAY);
      }
    }
    return XmlElementDescriptor.EMPTY_ARRAY;
  }

  @Override
  @Nullable
  public XmlFile getDescriptorFile() {
     return myFile;
   }

  @Override
  public PsiElement getDeclaration() {
     return myFile;
   }

  @Override
  public String getName(PsiElement context) {
    return null;
  }

  @Override
  public String getName() {
    return null;
  }

  @Override
  public void init(PsiElement element) {
    XmlDocument document = (XmlDocument) element;
    myFile = ((XmlFile)document.getContainingFile());
  }

  @Override
  public void validate(@NotNull XmlDocument context, @NotNull ValidationHost host) {}
}
