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
package com.intellij.lang.ant.validation;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.dom.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class AntAnnotator implements Annotator {

  public void annotate(@NotNull PsiElement psiElement, @NotNull final AnnotationHolder holder) {
    AntDomElement antElement = null;
    final XmlTag xmlTag = PsiTreeUtil.getParentOfType(psiElement, XmlTag.class, false);
    if (xmlTag != null) {
      antElement = AntSupport.getAntDomElement((xmlTag));
    }
    if (antElement == null)  {
      return;
    }

    antElement.accept(new AntDomRecursiveVisitor() {

      public void visitTypeDef(AntDomTypeDef typedef) {
        if (typedef.hasTypeLoadingErrors()) {
          createAnnotationOnTag(typedef, AntBundle.message("failed.to.load.types"), holder);
        }
        super.visitTypeDef(typedef);
      }

      public void visitAntDomCustomElement(AntDomCustomElement custom) {
        if (custom.getDefinitionClass() == null) {
          final AntDomNamedElement declaringElement = custom.getDeclaringElement();
          if (declaringElement instanceof AntDomTypeDef) {
            String failedMessage = AntBundle.message("using.definition.which.type.failed.to.load");
            final String error = custom.getLoadError();
            if (error != null) {
              failedMessage = failedMessage + ": " + error;
            }
            createAnnotationOnTag(custom, failedMessage, holder);
          }
        }
        super.visitAntDomCustomElement(custom);
      }

      public void visitAntDomElement(AntDomElement element) {
        for (GenericDomValue child : DomUtil.getDefinedChildrenOfType(element, GenericDomValue.class, false, true)) {
          final XmlElement valueElement = DomUtil.getValueElement(child);
          if (valueElement != null) {
            checkReferences(valueElement, holder);
          }
        }
        super.visitAntDomElement(element);
      }
    });
  }

  private static void createAnnotationOnTag(AntDomElement custom, String failedMessage, AnnotationHolder holder) {
    final XmlTag tag = custom.getXmlTag();
    if (tag == null) {
      return;
    }
    final String name = custom.getXmlElementName();
    final TextRange absoluteRange = new TextRange(0, name.length()).shiftRight(tag.getTextRange().getStartOffset() + 1);
    holder.createErrorAnnotation(absoluteRange, failedMessage);
  }

  //private static void addDefinitionQuickFixes(final Annotation annotation, final AntStructuredElement se) {
  //  if (se.getSourceElement().getName().length() == 0) return;
  //
  //  final AntProject project = se.getAntProject();
  //  annotation.registerFix(new AntCreateMacroDefFix(se));
  //  annotation.registerFix(new AntCreatePresetDefFix(se));
  //  for (final AntFile antFile : project.getImportedFiles()) {
  //    annotation.registerFix(new AntCreateMacroDefFix(se, antFile));
  //    annotation.registerFix(new AntCreatePresetDefFix(se, antFile));
  //  }
  //}

  //private static void checkValidAttributes(final AntStructuredElement se, final AntTypeDefinition def, final @NonNls AnnotationHolder holder) {
  //  final XmlTag sourceElement = se.getSourceElement();
  //  for (final XmlAttribute attr : sourceElement.getAttributes()) {
  //    @NonNls final String name = attr.getName();
  //    if (name.startsWith("xmlns")) continue;
  //    final AntAttributeType type = def.getAttributeType(name);
  //    final PsiElement attrName = attr.getFirstChild();
  //    if (attrName != null) {
  //      if (type == null) {
  //        if (!isSuccessorOfUndefinedElement(se)) {
  //          holder.createErrorAnnotation(attrName, AntBundle.message("attribute.is.not.allowed.here", name));
  //        }
  //      }
  //      else {
  //        final String attrValue = attr.getValue();
  //        if (type == AntAttributeType.INTEGER) {
  //          try {
  //            Integer.parseInt(se.computeAttributeValue(attrValue));
  //          }
  //          catch (NumberFormatException e) {
  //            holder.createErrorAnnotation(attrName, AntBundle.message("integer.attribute.has.invalid.value", name));
  //          }
  //        }
  //        else if (type == AntAttributeType.STRING) {
  //          if (attrValue != null && AntProperty.TSTAMP_PATTERN_ATTRIBUTE_NAME.equalsIgnoreCase(name)) {
  //            final PsiElement parent = se.getParent();
  //            if (parent instanceof AntProperty && ((AntProperty)parent).isTstamp()) {
  //              try {
  //                new SimpleDateFormat(attrValue);
  //              }
  //              catch (IllegalArgumentException e) {
  //                holder.createErrorAnnotation(attr.getValueElement(), e.getMessage());
  //              }
  //            }
  //          }
  //        }
  //      }
  //    }
  //  }
  //}

  private static void checkReferences(final XmlElement xmlElement, final @NonNls AnnotationHolder holder) {
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
        final TextRange absoluteRange = ref.getRangeInElement().shiftRight(ref.getElement().getTextRange().getStartOffset());
        final Annotation annotation = holder.createErrorAnnotation(absoluteRange, antDomRef.getUnresolvedMessagePattern());
        annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
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
