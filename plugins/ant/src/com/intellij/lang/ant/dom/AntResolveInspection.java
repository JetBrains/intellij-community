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

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.validation.AntInspection;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomHighlightingHelper;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class AntResolveInspection extends AntInspection {

  public static final String SHORT_NAME = "AntResolveInspection";

  @NotNull
  public String getDisplayName() {
    return "Reference resolve problems";
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
      if (typeDef.hasTypeLoadingErrors()) {
        holder.createProblem(typeDef, AntBundle.message("failed.to.load.types"));
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
      if (ref.resolve() == null) {
        holder.createProblem(domElement, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, antDomRef.getUnresolvedMessagePattern(), ref.getRangeInElement() /*todo add quickfixes*/);
        if (ref instanceof AntDomFileReference) {
          if (processed == null) {
            processed = new HashSet<PsiReference>();
          }
          ContainerUtil.addAll(processed, ((AntDomFileReference)ref).getFileReferenceSet().getAllReferences());
        }

        //final IntentionAction[] intentionActions = antRef.getFixes();
        //for (final IntentionAction action : intentionActions) {
        //  annotation.registerFix(action);
        //}
      }
    }
  }


}
