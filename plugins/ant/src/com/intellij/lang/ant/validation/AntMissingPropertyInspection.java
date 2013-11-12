/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.dom.*;
import com.intellij.lang.ant.quickfix.AntCreatePropertyFix;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomHighlightingHelper;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AntMissingPropertyInspection extends AntInspection {

  @NonNls private static final String SHORT_NAME = "AntMissingPropertyInspection";

  @Nls
  @NotNull
  public String getDisplayName() {
    return AntBundle.message("ant.missing.property.inspection");
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  protected void checkDomElement(DomElement element, DomElementAnnotationHolder holder, DomHighlightingHelper helper) {
    if (element instanceof GenericDomValue) {
      final XmlElement xmlElement = DomUtil.getValueElement(((GenericDomValue)element));
      if (xmlElement != null) {

        for (final PsiReference ref : xmlElement.getReferences()) {
          if (!(ref instanceof AntDomReference)) {
            continue;
          }
          final AntDomReference antDomRef = (AntDomReference)ref;
          if (antDomRef.shouldBeSkippedByAnnotator()) {
            continue;
          }

          if (antDomRef instanceof AntDomPropertyReference && ref.resolve() == null) {
            PsiFile containingFile = xmlElement.getContainingFile();
            AntDomProject antDomProject = AntSupport.getAntDomProject(containingFile);
            List<PsiFileSystemItem> propertyFiles = getPropertyFiles(antDomProject);


            List<LocalQuickFix> quickFixList = new ArrayList<LocalQuickFix>();
            String canonicalText = ((AntDomPropertyReference)antDomRef).getValue();
            quickFixList.add(new AntCreatePropertyFix(canonicalText, null));
            for (PsiFileSystemItem propertyFile : propertyFiles) {
              quickFixList.add(new AntCreatePropertyFix(canonicalText, propertyFile));
            }
            LocalQuickFix[] fixes = quickFixList.toArray((new LocalQuickFix[quickFixList.size()]));
            holder.createProblem(element, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, canonicalText, ref.getRangeInElement(), fixes);
          }
        }
      }
    }
  }

  @NotNull
  private static List<PsiFileSystemItem> getPropertyFiles(AntDomProject antDomProject) {
    List<PsiFileSystemItem> files = new ArrayList<PsiFileSystemItem>();
    for (Iterator<AntDomElement> iterator = antDomProject.getAntChildrenIterator(); iterator.hasNext(); ) {
      AntDomElement child = iterator.next();
      if (child instanceof AntDomProperty) {
        final AntDomProperty property = (AntDomProperty)child;
        final String fileName = property.getFile().getStringValue();
        if (fileName != null) {
          final PsiFileSystemItem file = property.getFile().getValue();
          files.add(file);
        }
      }
    }
    return files;
  }
}

