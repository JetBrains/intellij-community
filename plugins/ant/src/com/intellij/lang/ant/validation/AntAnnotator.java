package com.intellij.lang.ant.validation;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.psi.*;
import com.intellij.lang.ant.psi.impl.reference.AntPropertyReference;
import com.intellij.lang.ant.psi.impl.reference.AntReference;
import com.intellij.lang.ant.psi.introspection.AntAttributeType;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.quickfix.AntCreateMacroDefFix;
import com.intellij.lang.ant.quickfix.AntCreatePresetDefFix;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;

public class AntAnnotator implements Annotator {

  public void annotate(PsiElement psiElement, AnnotationHolder holder) {
    if (!(psiElement instanceof AntElement)) return;
    final AntElement element = (AntElement)psiElement;
    if (element instanceof AntStructuredElement) {
      final AntStructuredElement se = (AntStructuredElement)element;
      AntElement parent = se.getAntParent();
      final AntTypeDefinition def = se.getTypeDefinition();
      final String name = se.getSourceElement().getName();
      final TextRange absoluteRange = new TextRange(0, name.length()).shiftRight(se.getSourceElement().getTextOffset() + 1);
      if (def == null) {
        if (!isSuccessorOfUndefinedElement(se.getAntParent())) {
          boolean macroDefined = false;
          while (parent != null) {
            if (parent instanceof AntTask && ((AntTask)parent).isMacroDefined()) {
              macroDefined = true;
              break;
            }
            parent = parent.getAntParent();
          }
          final Annotation annotation = holder.createErrorAnnotation(absoluteRange, AntBundle.message("undefined.element", name));
          if (!macroDefined) {
            addDefinitionQuickFixes(annotation, se);
          }
        }
      }
      else {
        checkValidAttributes(se, def, holder);
        // impoted types wouldn't be registered as valid types of nested elements,
        // so we just don't check elements for nested validness if they have imported types
        if (!se.hasImportedTypeDefinition() && parent instanceof AntStructuredElement) {
          if (!canBeNested((AntStructuredElement)parent, def) && !isSuccessorOfUndefinedElement(se)) {
            holder.createErrorAnnotation(absoluteRange, AntBundle.message("nested.element.is.not.allowed.here", name));
          }
        }
        if (se instanceof AntTypeDef) {
          final AntTypeDef td = (AntTypeDef)se;
          if (!td.typesLoaded()) {
            String failedMessage = AntBundle.message("failed.to.load.types");
            if (td.getLocalizedError() != null) {
              failedMessage = failedMessage + ": " + td.getLocalizedError();
            }
            holder.createErrorAnnotation(absoluteRange, failedMessage);
          }
        }
        else if (se.isTypeDefined()) {
          final PsiElement de = def.getDefiningElement();
          if (de != null && !((AntTypeDef)de).typesLoaded()) {
            holder.createWarningAnnotation(absoluteRange, AntBundle.message("using.definition.which.type.failed.to.load", name));
          }
        }
      }
    }
    checkReferences(element, holder);
  }

  private static boolean canBeNested(final AntStructuredElement parent, final AntTypeDefinition maybeNestedDef) {
    final AntTypeDefinition parentDef = parent.getTypeDefinition();
    if (parentDef == null) {
      return false;
    }
    return 
      (parentDef.isAllTaskContainer() && maybeNestedDef.isTask()) ||
      (parentDef.getNestedClassName(maybeNestedDef.getTypeId()) != null) || 
      isExtensionPointType(parent, maybeNestedDef); 
  }

  private static boolean isExtensionPointType(final AntStructuredElement parent, final AntTypeDefinition maybeNested) {
    final AntTypeDefinition parentDef = parent.getTypeDefinition();
    return parentDef != null && parentDef.isExtensionPointType(parent.getAntFile().getClassLoader(), maybeNested.getClassName());
  }

  private static boolean isSuccessorOfUndefinedElement(AntElement element) {
    while (element instanceof AntStructuredElement) {
      final AntTypeDefinition def = ((AntStructuredElement)element).getTypeDefinition();
      if (def == null) {
        return true;
      }
      final PsiElement de = def.getDefiningElement();
      if (de != null && de instanceof AntTypeDef && !((AntTypeDef)de).typesLoaded()) {
        return true;
      }
      element = element.getAntParent();
    }
    return false;
  }

  private static boolean isReferencedPropertyUsedInCondition(AntPropertyReference propRef, final AntTarget.ConditionalAttribute conditionalAttribute) {
    final String referencedPropertyName = propRef.getCanonicalText();
    if (referencedPropertyName == null) {
      return false;
    }
    AntElement element = propRef.getElement();
    while (element instanceof AntStructuredElement) {
      if (element instanceof AntTarget) {
        final String conditionalProperty = ((AntTarget)element).getConditionalPropertyName(conditionalAttribute);
        if (referencedPropertyName.equals(conditionalProperty))  {
          return true;
        }
      }
      element = element.getAntParent();
    }
    return false;
  }

  private static void addDefinitionQuickFixes(final Annotation annotation, final AntStructuredElement se) {
    if (se.getSourceElement().getName().length() == 0) return;

    final AntProject project = se.getAntProject();
    annotation.registerFix(new AntCreateMacroDefFix(se));
    annotation.registerFix(new AntCreatePresetDefFix(se));
    for (final AntFile antFile : project.getImportedFiles()) {
      annotation.registerFix(new AntCreateMacroDefFix(se, antFile));
      annotation.registerFix(new AntCreatePresetDefFix(se, antFile));
    }
  }

  private static void checkValidAttributes(final AntStructuredElement se,
                                           final AntTypeDefinition def,
                                           final @NonNls AnnotationHolder holder) {
    final XmlTag sourceElement = se.getSourceElement();
    for (final XmlAttribute attr : sourceElement.getAttributes()) {
      @NonNls final String name = attr.getName();
      if (name.startsWith("xmlns")) continue;
      final AntAttributeType type = def.getAttributeType(name);
      final PsiElement attrName = attr.getFirstChild();
      if (attrName != null) {
        if (type == null) {
          if (!isSuccessorOfUndefinedElement(se)) {
            holder.createErrorAnnotation(attrName, AntBundle.message("attribute.is.not.allowed.here", name));
          }
        }
        else {
          if (type == AntAttributeType.INTEGER) {
            try {
              Integer.parseInt(se.computeAttributeValue(attr.getValue()));
            }
            catch (NumberFormatException e) {
              holder.createErrorAnnotation(attrName, AntBundle.message("integer.attribute.has.invalid.value", name));
            }
          }
        }
      }
    }
  }

  private static void checkReferences(AntElement element, final @NonNls AnnotationHolder holder) {
    final PsiReference[] refs = element.getReferences();
    for (final PsiReference ref : refs) {
      if (ref instanceof AntReference) {
        final AntReference antRef = (AntReference)ref;
        if (!antRef.shouldBeSkippedByAnnotator() && ref.resolve() == null) {
          if (antRef instanceof AntPropertyReference && isReferencedPropertyUsedInCondition((AntPropertyReference)antRef, AntTarget.ConditionalAttribute.IF)) {
            // in runtime, if execution reaches this task the property is defined since it is used in if-condition
            // so it is would be a mistake to highlight this as unresolved prop
            continue;
          }
          final TextRange absoluteRange = ref.getRangeInElement().shiftRight(ref.getElement().getTextRange().getStartOffset());
          final Annotation annotation = holder.createErrorAnnotation(absoluteRange, antRef.getUnresolvedMessagePattern());
          annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
          final IntentionAction[] intentionActions = antRef.getFixes();
          for (final IntentionAction action : intentionActions) {
            annotation.registerFix(action);
          }
        }
      }
    }
  }
}
