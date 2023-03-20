// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.resources;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.references.PropertyReference;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.XmlAttributeValue;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxPropertyAttributeDescriptor;

import java.util.Set;

public final class JavaFxResourcePropertyValueInspection extends XmlSuppressableInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    if (!JavaFxFileTypeFactory.isFxml(session.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new XmlElementVisitor() {
      @Override
      public void visitXmlAttributeValue(@NotNull XmlAttributeValue xmlAttributeValue) {
        super.visitXmlAttributeValue(xmlAttributeValue);
        final String value = xmlAttributeValue.getValue();
        if (value.startsWith("%") && value.length() > 1) {
          final PsiReference reference = xmlAttributeValue.getReference();
          if (reference instanceof PropertyReference) {
            final ResolveResult[] resolveResults = ((PropertyReference)reference).multiResolve(false);
            final Set<String> propertyValues = StreamEx
              .of(resolveResults)
              .map(ResolveResult::getElement)
              .select(Property.class)
              .map(Property::getValue)
              .nonNull()
              .toSet();
            if (!propertyValues.isEmpty()) {
              StreamEx
                .of(propertyValues)
                .map(propertyValue -> JavaFxPropertyAttributeDescriptor.validateLiteralOrEnumConstant(xmlAttributeValue, propertyValue))
                .nonNull()
                .distinct()
                .forEach((@InspectionMessage var errorMessage) -> holder.registerProblem(xmlAttributeValue, errorMessage));
            }
          }
        }
      }
    };
  }
}
