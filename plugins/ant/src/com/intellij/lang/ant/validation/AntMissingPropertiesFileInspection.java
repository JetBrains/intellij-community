// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.validation;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.dom.AntDomProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomHighlightingHelper;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class AntMissingPropertiesFileInspection extends AntInspection {

  private static final @NonNls String SHORT_NAME = "AntMissingPropertiesFileInspection";

  @Override
  public @NonNls @NotNull String getShortName() {
    return SHORT_NAME;
  }

  @Override
  protected void checkDomElement(@NotNull DomElement element, @NotNull DomElementAnnotationHolder holder, @NotNull DomHighlightingHelper helper) {
    if (element instanceof AntDomProperty property) {
      final GenericAttributeValue<PsiFileSystemItem> fileValue = property.getFile();
      final String fileName = fileValue.getStringValue();
      if (fileName != null) {
        final PropertiesFile propertiesFile = property.getPropertiesFile();
        if (propertiesFile == null) {
          final PsiFileSystemItem file = fileValue.getValue();
          if (file instanceof XmlFile) {
            holder.createProblem(fileValue, AntBundle.message("file.type.xml.not.supported", fileName));
          }
          else if (file instanceof PsiFile) {
            holder.createProblem(fileValue, AntBundle.message("file.type.not.supported", fileName));
          }
          else {
            holder.createProblem(fileValue, AntBundle.message("file.doesnt.exist", fileName));
          }
        }
      }
    }
  }

}

