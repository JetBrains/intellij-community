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
package com.intellij.lang.ant.dom;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.quickfix.AntChangeContextLocalFix;
import com.intellij.lang.ant.quickfix.AntCreatePropertyFix;
import com.intellij.lang.ant.quickfix.AntCreateTargetFix;
import com.intellij.lang.ant.validation.AntInspection;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomHighlightingHelper;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class AntResolveInspection extends AntInspection {

  public static final String SHORT_NAME = "AntResolveInspection";

  @NotNull
  public String getDisplayName() {
    return "Ant references resolve problems";
  }

  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  protected void checkDomElement(DomElement element, DomElementAnnotationHolder holder, DomHighlightingHelper helper) {
    if (element instanceof GenericDomValue) {
      final XmlElement valueElement = DomUtil.getValueElement(((GenericDomValue)element));
      if (valueElement != null) {
        checkReferences(valueElement, holder, element);
      }
    }
    else if (element instanceof AntDomTypeDef) {
      final AntDomTypeDef typeDef = (AntDomTypeDef)element;
      final List<String> errors = typeDef.getErrorDescriptions();
      if (!errors.isEmpty()) {
        final StringBuilder builder = new StringBuilder();
        builder.append(AntBundle.message("failed.to.load.types")).append(":");
        for (String error : errors) {
          builder.append("\n").append(error);
        }
        holder.createProblem(typeDef, builder.toString());
      }
    }
    else if (element instanceof AntDomCustomElement) {
      final AntDomCustomElement custom = (AntDomCustomElement)element;
      if (custom.getDefinitionClass() == null) {
        final AntDomNamedElement declaringElement = custom.getDeclaringElement();
        if (declaringElement instanceof AntDomTypeDef) {
          String failedMessage = AntBundle.message("using.definition.which.type.failed.to.load");
          final String error = custom.getLoadError();
          if (error != null) {
            failedMessage = failedMessage + ": " + error;
          }
          holder.createProblem(custom, failedMessage);
        }
      }
    }
  }
  
  private static void checkReferences(final XmlElement xmlElement, final @NonNls DomElementAnnotationHolder holder, DomElement domElement) {
    if (xmlElement == null) {
      return;
    }
    Set<PsiReference> processed = null;
    Collection<PropertiesFile> propertyFiles = null; // to be initialized lazily
    for (final PsiReference ref : xmlElement.getReferences()) {
      if (!(ref instanceof AntDomReference)) {
        continue;
      }
      final AntDomReference antDomRef = (AntDomReference)ref;
      if (antDomRef.shouldBeSkippedByAnnotator()) {
        continue;
      }
      if (processed != null && processed.contains(ref)) {
        continue;
      }

      if (!isResolvable(ref)) {
        final List<LocalQuickFix> quickFixList = new SmartList<>();
        quickFixList.add(new AntChangeContextLocalFix());

        if (ref instanceof AntDomPropertyReference) {
          final String canonicalText = ref.getCanonicalText();
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
        }
        else if (ref instanceof AntDomTargetReference) {
          quickFixList.add(new AntCreateTargetFix(ref.getCanonicalText()));
        }

        holder.createProblem(
          domElement,
          ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
          antDomRef.getUnresolvedMessagePattern(),
          ref.getRangeInElement(),
          quickFixList.toArray((new LocalQuickFix[quickFixList.size()]))
        );

        if (ref instanceof AntDomFileReference) {
          if (processed == null) {
            processed = new HashSet<>();
          }
          ContainerUtil.addAll(processed, ((AntDomFileReference)ref).getFileReferenceSet().getAllReferences());
        }
      }
    }
  }

  private static boolean isResolvable(PsiReference ref) {
    if (ref.resolve() != null) {
      return true;
    }
    if (ref instanceof PsiPolyVariantReference) {
      return ((PsiPolyVariantReference)ref).multiResolve(false).length > 0;
    }
    return false;
  }

  @NotNull
  private static Collection<PropertiesFile> getPropertyFiles(@Nullable AntDomProject antDomProject, @NotNull XmlElement stopElement) {
    if (antDomProject == null) {
      return Collections.emptyList();
    }
    final Set<PropertiesFile> files = new java.util.HashSet<>();
    final int stopOffset = stopElement.getTextOffset();

    for (Iterator<AntDomElement> iterator = antDomProject.getAntChildrenIterator(); iterator.hasNext(); ) {
      AntDomElement child = iterator.next();
      final XmlElement xmlElement = child.getXmlElement();
      if (xmlElement != null && xmlElement.getTextOffset() >= stopOffset) {
        break; // no need to offer to add properties to files that are imported after the property reference
      }
      if (child instanceof AntDomProperty) {
        final AntDomProperty property = (AntDomProperty)child;
        final PropertiesFile file = property.getPropertiesFile();
        if (file != null) {
          files.add(file);
        }
      }
    }
    return files;
  }

}
