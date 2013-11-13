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
import com.intellij.lang.ant.dom.AntDomElement;
import com.intellij.lang.ant.dom.AntDomProject;
import com.intellij.lang.ant.dom.AntDomProperty;
import com.intellij.lang.ant.dom.AntDomPropertyReference;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class AntMissingPropertyInspection extends AntInspection {

  private static final String SHORT_NAME = "AntMissingPropertyInspection";

  @Nls
  @NotNull
  public String getDisplayName() {
    return AntBundle.message("ant.missing.property.inspection");
  }

  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  protected void checkDomElement(DomElement element, DomElementAnnotationHolder holder, DomHighlightingHelper helper) {
    if (element instanceof GenericDomValue) {
      final XmlElement xmlElement = DomUtil.getValueElement(((GenericDomValue)element));
      if (xmlElement != null) {
        Collection<PropertiesFile> propertyFiles = null; // to be initialized lazily

        for (final PsiReference ref : xmlElement.getReferences()) {
          if (!(ref instanceof AntDomPropertyReference)) {
            continue;
          }
          final AntDomPropertyReference antDomRef = (AntDomPropertyReference)ref;
          if (antDomRef.shouldBeSkippedByAnnotator()) {
            continue;
          }

          if (antDomRef.resolve() == null) {
            final List<LocalQuickFix> quickFixList = new ArrayList<LocalQuickFix>();

            final String canonicalText = antDomRef.getCanonicalText();
            quickFixList.add(new AntCreatePropertyFix(canonicalText, null));

            final PsiFile containingFile = xmlElement.getContainingFile();
            if (containingFile != null) {
              if (propertyFiles == null) {
                propertyFiles = getPropertyFiles(AntSupport.getAntDomProject(containingFile), xmlElement);
              }
              for (PropertiesFile propertyFile : propertyFiles) {
                quickFixList.add(new AntCreatePropertyFix(canonicalText, propertyFile));
              }
            }

            holder.createProblem(
              element,
              ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
              canonicalText,
              ref.getRangeInElement(),
              quickFixList.toArray((new LocalQuickFix[quickFixList.size()]))
            );
          }
        }
      }
    }
  }

  @NotNull
  private static Collection<PropertiesFile> getPropertyFiles(@Nullable AntDomProject antDomProject, @NotNull XmlElement stopElement) {
    if (antDomProject == null) {
      return Collections.emptyList();
    }
    final Set<PropertiesFile> files = new HashSet<PropertiesFile>();
    final int stopOffset = stopElement.getTextOffset();

    for (Iterator<AntDomElement> iterator = antDomProject.getAntChildrenIterator(); iterator.hasNext(); ) {
      AntDomElement child = iterator.next();
      final XmlElement xmlElement = child.getXmlElement();
      if (xmlElement != null && xmlElement.getTextOffset() >= stopOffset) {
        break; // no need to offer to add properties to files that are imported after the property reference
      }
      if (child instanceof AntDomProperty) {
        final AntDomProperty property = (AntDomProperty)child;
        final PsiFileSystemItem file = property.getFile().getValue();
        if (file instanceof PropertiesFile) {
          files.add((PropertiesFile)file);
        }
      }
    }
    return files;
  }
}

