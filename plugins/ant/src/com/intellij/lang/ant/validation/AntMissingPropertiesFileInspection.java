/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

public class AntMissingPropertiesFileInspection extends AntInspection {

  @NonNls private static final String SHORT_NAME = "AntMissingPropertiesFileInspection";

  @Override
  @NonNls
  @NotNull
  public String getShortName() {
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

