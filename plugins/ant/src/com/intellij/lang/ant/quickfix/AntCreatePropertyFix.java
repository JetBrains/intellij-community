// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.dom.AntDomTarget;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

public class AntCreatePropertyFix implements LocalQuickFix {
  private static final @NonNls String PROPERTY = "property";
  private static final @NonNls String NAME_ATTR = "name";
  private static final @NonNls String VALUE_ATTR = "value";
  private final @NlsSafe String myCanonicalText;
  private final @Nullable PropertiesFile myPropFile;

  public AntCreatePropertyFix(@NlsSafe String canonicalText, @Nullable PropertiesFile propertiesFile) {
    myCanonicalText = canonicalText;
    myPropFile = propertiesFile;
  }

  @Override
  public @NotNull String getName() {
    if (myPropFile != null) {
      return AntBundle.message("create.property.in.file.quickfix.name", myCanonicalText, myPropFile.getName());
    }
    return AntBundle.message("create.property.quickfix.name", myCanonicalText);
  }

  @Override
  public @NotNull String getFamilyName() {
    return AntBundle.message("ant.intention.create.property.family.name");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement psiElement = descriptor.getPsiElement();
    final PsiFile containingFile = psiElement.getContainingFile();

    final FileModificationService modificationService = FileModificationService.getInstance();
    Navigatable result = null;
    if (myPropFile != null) {
      final VirtualFile vFile = myPropFile.getVirtualFile();

      if (myPropFile instanceof PsiFile) {
        if (!modificationService.prepareFileForWrite((PsiFile)myPropFile)) {
          return;
        }
      }
      else if (vFile != null) {
        if (!modificationService.prepareVirtualFilesForWrite(project, Collections.singleton(vFile))) {
          return;
        }
      }

      result = WriteAction.compute(() -> {
        final IProperty generatedProperty = myPropFile.addProperty(myCanonicalText, "");
        return vFile != null
               ? PsiNavigationSupport.getInstance().createNavigatable(project, vFile,
                                                                      generatedProperty.getPsiElement().getTextRange()
                                                                                       .getEndOffset())
               : generatedProperty;
      });
    }
    else {
      if (containingFile instanceof XmlFile xmlFile) {
        final XmlTag rootTag = xmlFile.getRootTag();
        if (!modificationService.prepareFileForWrite(xmlFile)) {
          return;
        }
        if (rootTag != null) {
          result = WriteAction.compute(() -> {
            final XmlTag propTag = rootTag.createChildTag(PROPERTY, rootTag.getNamespace(), null, false);
            propTag.setAttribute(NAME_ATTR, myCanonicalText);
            propTag.setAttribute(VALUE_ATTR, "");
            final DomElement contextElement = DomUtil.getDomElement(descriptor.getPsiElement());
            PsiElement generated;
            if (contextElement == null) {
              generated = rootTag.addSubTag(propTag, true);
            }
            else {
              final AntDomTarget containingTarget = contextElement.getParentOfType(AntDomTarget.class, false);
              final DomElement anchor = containingTarget != null ? containingTarget : contextElement;
              final XmlTag tag = anchor.getXmlTag();
              if (!rootTag.equals(tag)) {
                generated = tag.getParent().addBefore(propTag, tag);
              }
              else {
                generated = rootTag.addSubTag(propTag, true);
              }
            }
            Navigatable navigatable = null;
            if (generated instanceof XmlTag) {
              final XmlAttribute valueAttrib = ((XmlTag)generated).getAttribute(VALUE_ATTR);
              if (valueAttrib != null) {
                final XmlAttributeValue valueElement = valueAttrib.getValueElement();
                if (valueElement instanceof Navigatable) {
                  navigatable = (Navigatable)valueElement;
                }
              }
            }
            if (navigatable == null && generated instanceof Navigatable) {
              navigatable = (Navigatable)generated;
            }
            return navigatable;
          });
        }
      }
    }

    if (result != null) {
      result.navigate(true);
    }
  }
}
