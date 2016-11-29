package org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.psi.*;
import com.intellij.psi.xml.*;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxPropertyAttributeDescriptor;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxPropertyTagDescriptor;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxColorRgbInspection extends XmlSuppressableInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    if (!JavaFxFileTypeFactory.isFxml(session.getFile())) return PsiElementVisitor.EMPTY_VISITOR;

    return new XmlElementVisitor() {
      @Override
      public void visitXmlAttribute(XmlAttribute attribute) {
        super.visitXmlAttribute(attribute);

        final String attributeValue = attribute.getValue();
        if (attributeValue == null) return;
        final XmlAttributeDescriptor descriptor = attribute.getDescriptor();
        if (descriptor instanceof JavaFxPropertyAttributeDescriptor) {
          final PsiClass psiClass = ((JavaFxPropertyAttributeDescriptor)descriptor).getPsiClass();
          if (psiClass != null && JavaFxCommonNames.JAVAFX_SCENE_COLOR.equals(psiClass.getQualifiedName())) {
            final XmlAttributeValue valueElement = attribute.getValueElement();
            final PsiElement location = valueElement != null ? valueElement : attribute;
            validateColorComponent(psiClass, attribute.getName(), attributeValue, location);
          }
        }
      }

      @Override
      public void visitXmlTag(XmlTag tag) {
        super.visitXmlTag(tag);
        if (tag.getSubTags().length != 0) return;

        final XmlElementDescriptor descriptor = tag.getDescriptor();
        if (descriptor instanceof JavaFxPropertyTagDescriptor) {
          final PsiClass psiClass = ((JavaFxPropertyTagDescriptor)descriptor).getPsiClass();
          if (psiClass != null && JavaFxCommonNames.JAVAFX_SCENE_COLOR.equals(psiClass.getQualifiedName())) {
            final XmlTagValue valueElement = tag.getValue();
            final XmlText[] textElements = valueElement.getTextElements();
            final PsiElement location = textElements.length == 1 ? textElements[0] : tag;
            validateColorComponent(psiClass, tag.getName(), valueElement.getTrimmedText(), location);
          }
        }
      }

      private void validateColorComponent(@NotNull PsiClass psiClass,
                                          @NotNull String propertyName,
                                          @NotNull String propertyValue,
                                          @NotNull PsiElement location) {
        final PsiMember declaration = JavaFxPsiUtil.collectWritableProperties(psiClass).get(propertyName);
        final String boxedQName = JavaFxPsiUtil.getBoxedPropertyType(psiClass, declaration);
        if (CommonClassNames.JAVA_LANG_FLOAT.equals(boxedQName) || CommonClassNames.JAVA_LANG_DOUBLE.equals(boxedQName)) {
          try {
            double value = Double.parseDouble(propertyValue);
            if (value < 0.0 || value > 1.0) {
              holder.registerProblem(location, "Color component has to be a number between 0.0 and 1.0, inclusively");
            }
          }
          catch (NumberFormatException ignored) {
          }
        }
      }
    };
  }
}
