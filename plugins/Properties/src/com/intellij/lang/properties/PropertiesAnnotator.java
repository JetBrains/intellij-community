package com.intellij.lang.properties;

import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.util.Comparing;

/**
 * @author cdr
 */
class PropertiesAnnotator implements Annotator {
  public void annotate(PsiElement element, AnnotationHolder holder) {
    if (!(element instanceof Property)) return;
    Property origProperty = (Property)element;
    PropertiesFile propertiesFile = (PropertiesFile)element.getContainingFile();
    final Property[] allProperties = propertiesFile.getProperties();
    int dupProperties = 0;
    for (int i = 0; i < allProperties.length; i++) {
      Property property = allProperties[i];
      if (property == element) continue;
      if (Comparing.strEqual(property.getKey(), origProperty.getKey())) {
        dupProperties++;
        if (dupProperties > 0) break;
      }
    }
    if (dupProperties > 0) {
      holder.createErrorAnnotation(((PropertyImpl)origProperty).getKeyNode(), "Duplicate property key");
    }
  }
}
