package com.intellij.lang.ant.validation;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntDefaultIntrospector;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntTask;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;

public class AntAnnotator implements Annotator {

  public void annotate(PsiElement psiElement, AnnotationHolder holder) {
    AntElement element = (AntElement)psiElement;
    if (element instanceof AntTask) {
      final AntTask task = (AntTask)element;
      checkValidAttributes(task, holder);
      checkValidNestedElements(task, holder);
    }
  }

  private static void checkValidAttributes(AntTask task, AnnotationHolder holder) {
    final XmlTag sourceElement = task.getSourceElement();
    final String taskName = sourceElement.getName();
    for (XmlAttribute attr : sourceElement.getAttributes()) {
      final String attrName = attr.getName();
      if (AntDefaultIntrospector.getTaskAttributeType(taskName, attrName) == null) {
        holder.createErrorAnnotation(task, AntBundle.getMessage("attribute.is.not.allowed.for.the.task"));
      }
    }
  }

  private static void checkValidNestedElements(AntTask task, AnnotationHolder holder) {
    final XmlTag sourceElement = task.getSourceElement();
    final String taskName = sourceElement.getName();
    for (XmlTag tag : sourceElement.getSubTags()) {
      final String attrName = tag.getName();
      if (AntDefaultIntrospector.getTaskNestedElementType(taskName, attrName) == null) {
        holder.createErrorAnnotation(task, AntBundle.getMessage("nested.element.is.not.allowed.for.the.task"));
      }
    }
  }
}
