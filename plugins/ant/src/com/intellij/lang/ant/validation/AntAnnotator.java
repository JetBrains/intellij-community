package com.intellij.lang.ant.validation;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.psi.*;
import com.intellij.lang.ant.psi.impl.reference.AntGenericReference;
import com.intellij.lang.ant.psi.introspection.AntAttributeType;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.quickfix.AntCreateMacroDefAction;
import com.intellij.lang.ant.quickfix.AntCreatePresetDefAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;

public class AntAnnotator implements Annotator {

  public void annotate(PsiElement psiElement, AnnotationHolder holder) {
    if (!(psiElement instanceof AntElement)) return;
    AntElement element = (AntElement)psiElement;
    if (element instanceof AntStructuredElement) {
      final AntStructuredElement se = (AntStructuredElement)element;
      AntElement parent = se.getAntParent();
      AntTypeDefinition def = se.getTypeDefinition();
      final String name = se.getSourceElement().getName();
      if (def == null) {
        final Annotation annotation = holder.createErrorAnnotation(se, AntBundle.message("undefined.element", name));
        boolean defined = false;
        while (!(parent instanceof AntFile)) {
          if (parent instanceof AntTask && ((AntTask)parent).isMacroDefined()) {
            defined = true;
            break;
          }
          parent = parent.getAntParent();
        }
        if (!defined) {
          addDefinitionQuickFixes(annotation, se);
        }
      }
      else {
        checkValidAttributes(se, def, holder);
        // impoted types wouldn't be registered as valid types of nested elements,
        // so we just don't check elements for nested validness if they have imported types
        if (!se.hasImportedTypeDefinition() && parent instanceof AntStructuredElement) {
          final AntStructuredElement pe = (AntStructuredElement)parent;
          final AntTypeDefinition parentDef = pe.getTypeDefinition();
          if (parentDef != null && parentDef.getNestedClassName(def.getTypeId()) == null) {
            final TextRange textRange = new TextRange(0, name.length()).shiftRight(se.getSourceElement().getTextOffset());
            holder.createErrorAnnotation(textRange, AntBundle.message("nested.element.is.not.allowed.here", name));
          }
        }
      }
    }
    checkReferences(element, holder);
  }

  private static void addDefinitionQuickFixes(final Annotation annotation, final AntStructuredElement se) {
    final AntProject project = se.getAntProject();
    annotation.registerFix(new AntCreateMacroDefAction(se));
    annotation.registerFix(new AntCreatePresetDefAction(se));
    for (final AntFile antFile : project.getImportedFiles()) {
      annotation.registerFix(new AntCreateMacroDefAction(se, antFile));
      annotation.registerFix(new AntCreatePresetDefAction(se, antFile));
    }
  }

  private static void checkValidAttributes(AntStructuredElement se, AntTypeDefinition def, AnnotationHolder holder) {
    final XmlTag sourceElement = se.getSourceElement();
    for (final XmlAttribute attr : sourceElement.getAttributes()) {
      final String name = attr.getName();
      final AntAttributeType type = def.getAttributeType(name);
      if (type == null) {
        holder.createErrorAnnotation(attr, AntBundle.message("attribute.is.not.allowed.here", name));
      }
      else {
        final String value = attr.getValue();
        if (type == AntAttributeType.INTEGER) {
          try {
            Integer.parseInt(value);
          }
          catch (NumberFormatException e) {
            holder.createErrorAnnotation(attr, AntBundle.message("integer.attribute.has.invalid.value", name));
          }
        }
      }
    }
  }

  private static void checkReferences(AntElement element, @NonNls AnnotationHolder holder) {
    final PsiReference[] refs = element.getReferences();
    for (final PsiReference ref : refs) {
      if (ref instanceof AntGenericReference) {
        final AntGenericReference genRef = (AntGenericReference)ref;
        if (!genRef.shouldBeSkippedByAnnotator() && ref.resolve() == null) {
          final TextRange absoluteRange = ref.getRangeInElement().shiftRight(ref.getElement().getTextRange().getStartOffset());
          final Annotation annotation = holder.createErrorAnnotation(absoluteRange, genRef.getUnresolvedMessagePattern());
          if( genRef.getUnresolvedMessagePattern().startsWith("Cannot resolve symbol")) {
            continue;
          }
          annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
          final IntentionAction[] intentionActions = genRef.getFixes();
          for (final IntentionAction action : intentionActions) {
            annotation.registerFix(action);
          }
        }
      }
    }
  }
}
