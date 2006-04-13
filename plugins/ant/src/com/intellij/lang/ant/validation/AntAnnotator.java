package com.intellij.lang.ant.validation;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntTask;
import com.intellij.lang.ant.psi.impl.reference.AntGenericReference;
import com.intellij.lang.ant.psi.introspection.AntAttributeType;
import com.intellij.lang.ant.psi.introspection.AntTaskDefinition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;

public class AntAnnotator implements Annotator {

  public void annotate(PsiElement psiElement, AnnotationHolder holder) {
    AntElement element = (AntElement)psiElement;
    if (element instanceof AntTask) {
      final AntTask task = (AntTask)element;
      AntTaskDefinition def = task.getTaskDefinition();
      if (def == null) {
        holder.createErrorAnnotation(task, AntBundle.getMessage("undefined.task", task.getName()));
      }
      else {
        checkValidAttributes(task, def, holder);
        final AntElement parent = task.getAntParent();
        if (parent instanceof AntTask) {
          final AntTask parentTask = (AntTask)parent;
          final AntTaskDefinition parentDef = parentTask.getTaskDefinition();
          if (parentDef != null && parentDef.getNestedClassName(def.getTaskId()) == null) {
            final TextRange textRange = new TextRange(0, task.getName().length()).shiftRight(task.getSourceElement().getTextOffset());
            holder.createErrorAnnotation(textRange, AntBundle.getMessage("nested.element.is.not.allowed.for.the.task"));
          }
        }
      }
    }
    checkReferences(element, holder);
  }

  private static void checkValidAttributes(AntTask task, AntTaskDefinition def, AnnotationHolder holder) {
    final XmlTag sourceElement = task.getSourceElement();
    for (XmlAttribute attr : sourceElement.getAttributes()) {
      final AntAttributeType type = def.getAttributeType(attr.getName());
      if (type == null) {
        holder.createErrorAnnotation(task, AntBundle.getMessage("attribute.is.not.allowed.for.the.task"));
      }
      else {
        final String value = attr.getValue();
        if (type == AntAttributeType.INTEGER) {
          try {
            Integer.parseInt(value);
          }
          catch (NumberFormatException e) {
            holder.createErrorAnnotation(attr, AntBundle.getMessage("integer.attribute.has.invalid.value"));
          }
        }
      }
    }
  }

  private static void checkReferences(AntElement element, @NonNls AnnotationHolder holder) {
    PsiReference[] refs = element.getReferences();
    for (PsiReference ref : refs) {
      if (ref.resolve() == null) {
        final TextRange absoluteRange = ref.getRangeInElement().shiftRight(ref.getElement().getTextRange().getStartOffset());
        holder.createErrorAnnotation(absoluteRange, ((AntGenericReference)ref).getUnresolvedMessagePattern());
      }
    }
  }
}
