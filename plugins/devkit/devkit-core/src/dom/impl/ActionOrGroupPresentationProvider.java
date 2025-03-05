// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.presentation.PresentationProvider;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.ProjectIconsAccessor;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.ActionOrGroup;
import org.jetbrains.idea.devkit.dom.AddToGroup;
import org.jetbrains.idea.devkit.dom.Group;
import org.jetbrains.idea.devkit.dom.Reference;
import org.jetbrains.idea.devkit.references.ActionOrGroupIdReference;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UField;
import org.jetbrains.uast.UastContextKt;

import javax.swing.*;

public class ActionOrGroupPresentationProvider extends PresentationProvider<ActionOrGroup> {

  @Override
  public @Nullable Icon getIcon(@Nullable ActionOrGroup actionOrGroup) {
    return getIconForActionOrGroup(actionOrGroup);
  }

  public static class ForReference extends PresentationProvider<Reference> {

    @Override
    public @Nullable Icon getIcon(Reference reference) {
      //noinspection deprecation
      if (DomUtil.hasXml(reference.getId())) {
        //noinspection deprecation
        return getIconForActionOrGroup(resolveActionOrGroup(reference.getId()));
      }

      return getIconForActionOrGroup(resolveActionOrGroup(reference.getRef()));
    }
  }

  public static class ForAddToGroup extends PresentationProvider<AddToGroup> {

    @Override
    public @Nullable Icon getIcon(AddToGroup addToGroup) {
      return getIconForActionOrGroup(resolveActionOrGroup(addToGroup.getGroupId()));
    }
  }

  private static @Nullable Icon getIconForActionOrGroup(@Nullable ActionOrGroup actionOrGroup) {
    if (actionOrGroup == null || !DomUtil.hasXml(actionOrGroup.getIcon())) {
      return actionOrGroup instanceof Group ? AllIcons.Actions.GroupByPackage : null;
    }

    XmlAttributeValue iconAttrValue = actionOrGroup.getIcon().getXmlAttributeValue();
    assert iconAttrValue != null;

    boolean referenceFound = false;
    for (PsiReference reference : iconAttrValue.getReferences()) {
      referenceFound = true;
      Icon icon = getIconFromReference(reference);
      if (icon != null) {
        return icon;
      }
    }

    // icon field initializer may not be available if there're no attached sources for containing class
    if (referenceFound) {
      String value = iconAttrValue.getValue();
      Icon icon = IconLoader.findIcon(value, ActionOrGroupPresentationProvider.class, false, false);
      if (icon != null) {
        return icon;
      }
    }
    return null;
  }

  private static @Nullable ActionOrGroup resolveActionOrGroup(@Nullable GenericDomValue<String> actionOrGroupReferenceDomElement) {
    if (actionOrGroupReferenceDomElement == null) return null;
    XmlElement xmlElement = actionOrGroupReferenceDomElement.getXmlElement();
    if (xmlElement == null) return null;

    for (PsiReference reference : xmlElement.getReferences()) {
      if (reference instanceof ActionOrGroupIdReference) {
        PsiElement resolve = reference.resolve();
        DomElement domElement = DomUtil.getDomElement(resolve);
        if (domElement instanceof ActionOrGroup actionOrGroup) {
          return actionOrGroup;
        }
        break;
      }
    }
    return null;
  }

  private static @Nullable Icon getIconFromReference(@NotNull PsiReference reference) {
    PsiElement resolved = reference.resolve();
    if (!(resolved instanceof PsiField)) {
      return null;
    }
    UField field = UastContextKt.toUElement(resolved, UField.class);
    assert field != null;
    UExpression expression = field.getUastInitializer();
    if (expression == null) {
      return null;
    }

    ProjectIconsAccessor iconsAccessor = ProjectIconsAccessor.getInstance(resolved.getProject());
    VirtualFile iconFile = iconsAccessor.resolveIconFile(expression);
    return iconFile == null ? null : iconsAccessor.getIcon(iconFile);
  }
}
