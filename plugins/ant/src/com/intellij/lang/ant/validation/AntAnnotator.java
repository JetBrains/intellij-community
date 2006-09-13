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

import java.util.HashMap;

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
        if (!isLegateeOfUndefinedElement(se)) {
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
          final AntStructuredElement pe = (AntStructuredElement)parent;
          final AntTypeDefinition parentDef = pe.getTypeDefinition();
          if (parentDef != null && parentDef.getNestedClassName(def.getTypeId()) == null && !isLegateeOfUndefinedElement(se)) {
            holder.createErrorAnnotation(absoluteRange, AntBundle.message("nested.element.is.not.allowed.here", name));
          }
        }
        if (se instanceof AntTypeDef) {
          final AntTypeDef td = (AntTypeDef)se;
          if (!td.typesLoaded()) {
            holder.createErrorAnnotation(absoluteRange, AntBundle.message("failed.to.load.types", name));
          }
        }
        else if (se.isTypeDefined()) {
          final PsiElement de = def.getDefiningElement();
          if (de != null && !((AntTypeDef)de).typesLoaded()) {
            holder.createWarningAnnotation(absoluteRange, AntBundle.message("using.definition.which.type.failed.to.load", name));
          }
        }
        if (se instanceof AntProject) {
          checkDuplicateTargets((AntProject)se, holder);
        }
      }
    }
    checkReferences(element, holder);
  }

  private static boolean isLegateeOfUndefinedElement(final AntStructuredElement se) {
    AntElement parent = se;
    while (!((parent = parent.getAntParent()) instanceof AntProject) && parent != null) {
      final AntStructuredElement sp = (AntStructuredElement)parent;
      final AntTypeDefinition def = sp.getTypeDefinition();
      if (def != null) {
        return true;
      }
    }
    return false;
  }

  private static void addDefinitionQuickFixes(final Annotation annotation, final AntStructuredElement se) {
    if(se.getSourceElement().getName().length() == 0) return;

    final AntProject project = se.getAntProject();
    annotation.registerFix(new AntCreateMacroDefAction(se));
    annotation.registerFix(new AntCreatePresetDefAction(se));
    for (final AntFile antFile : project.getImportedFiles()) {
      annotation.registerFix(new AntCreateMacroDefAction(se, antFile));
      annotation.registerFix(new AntCreatePresetDefAction(se, antFile));
    }
  }

  private static void checkValidAttributes(final AntStructuredElement se,
                                           final AntTypeDefinition def,
                                           final @NonNls AnnotationHolder holder) {
    final XmlTag sourceElement = se.getSourceElement();
    for (final XmlAttribute attr : sourceElement.getAttributes()) {
      final String name = attr.getName();
      final AntAttributeType type = def.getAttributeType(name);
      final PsiElement attrName = attr.getFirstChild();
      if (attrName != null) {
        if (type == null) {
          if (!isLegateeOfUndefinedElement(se)) {
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

  private static void checkDuplicateTargets(final AntProject project, final @NonNls AnnotationHolder holder) {
    final AntTarget[] targets = project.getTargets();
    if (targets.length > 0) {
      final HashMap<String, AntTarget> name2Target = new HashMap<String, AntTarget>();
      for (final AntTarget target : targets) {
        final String name = target.getName();
        final AntTarget t = name2Target.get(name);
        if (t != null) {
          final String duplicatedMessage = AntBundle.message("target.is.duplicated", name);
          holder.createErrorAnnotation(t, duplicatedMessage);
          holder.createErrorAnnotation(target, duplicatedMessage);
        }
        name2Target.put(name, target);
      }
      final AntTarget[] importedTargets = project.getImportedTargets();
      for (final AntTarget target : importedTargets) {
        final String name = target.getName();
        final AntTarget t = name2Target.get(name);
        if (t != null) {
          holder.createErrorAnnotation(t, AntBundle.message("target.is.duplicated.in.imported.file", name, target.getAntFile().getName()));
        }
      }
    }
  }

  private static void checkReferences(AntElement element, final @NonNls AnnotationHolder holder) {
    final PsiReference[] refs = element.getReferences();
    for (final PsiReference ref : refs) {
      if (ref instanceof AntGenericReference) {
        final AntGenericReference genRef = (AntGenericReference)ref;
        if (!genRef.shouldBeSkippedByAnnotator() && ref.resolve() == null) {
          final TextRange absoluteRange = ref.getRangeInElement().shiftRight(ref.getElement().getTextRange().getStartOffset());
          final Annotation annotation = holder.createErrorAnnotation(absoluteRange, genRef.getUnresolvedMessagePattern());
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
