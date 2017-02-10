package org.jetbrains.plugins.javaFX.resources;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.references.PropertyReference;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttributeValue;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxPropertyAttributeDescriptor;

import java.util.Set;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxResourcePropertyValueInspection extends XmlSuppressableInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    if (!JavaFxFileTypeFactory.isFxml(session.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new XmlElementVisitor() {
      @Override
      public void visitXmlAttributeValue(XmlAttributeValue xmlAttributeValue) {
        super.visitXmlAttributeValue(xmlAttributeValue);
        final String value = xmlAttributeValue.getValue();
        if (value != null && value.startsWith("%") && value.length() > 1) {
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
                .forEach(errorMessage -> holder.registerProblem(xmlAttributeValue, errorMessage));
            }
          }
        }
      }
    };
  }
}
