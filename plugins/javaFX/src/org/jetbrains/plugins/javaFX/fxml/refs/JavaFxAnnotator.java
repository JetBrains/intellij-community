/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.codeInsight.intentions.XmlChooseColorIntentionAction;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.ColorIcon;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonClassNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxPropertyAttributeDescriptor;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;

/**
 * User: anna
 */
public class JavaFxAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull final PsiElement element, @NotNull AnnotationHolder holder) {
    final PsiFile containingFile = element.getContainingFile();
    if (!JavaFxFileTypeFactory.isFxml(containingFile)) return;
    if (element instanceof XmlAttributeValue) {
      final PsiReference[] references = element.getReferences();
      if (!JavaFxPsiUtil.isExpressionBinding(((XmlAttributeValue)element).getValue())) {
        for (PsiReference reference : references) {
          final PsiElement resolve = reference.resolve();
          if (resolve instanceof PsiMember) {
            if (!JavaFxPsiUtil.isVisibleInFxml((PsiMember)resolve)) {
              final String symbolPresentation = "'" + SymbolPresentationUtil.getSymbolPresentableText(resolve) + "'";
              final Annotation annotation = holder.createErrorAnnotation(element, 
                                                                         symbolPresentation + (resolve instanceof PsiClass ? " should be public" : " should be public or annotated with @FXML"));
              if (!(resolve instanceof PsiClass)) {
                annotation.registerUniversalFix(new AddAnnotationFix(JavaFxCommonClassNames.JAVAFX_FXML_ANNOTATION, (PsiMember)resolve, ArrayUtil.EMPTY_STRING_ARRAY), null, null);
              }
            }
          }
        }
      }
      if (references.length == 0) {
        final String attributeValueText = StringUtil.stripQuotesAroundValue(element.getText());
        if (attributeValueText.startsWith("#")) {
          attachColorIcon(element, holder, attributeValueText);
        }
      }
    } else if (element instanceof XmlAttribute) {
      final String attributeName = ((XmlAttribute)element).getName();
      if (!FxmlConstants.FX_DEFAULT_PROPERTIES.contains(attributeName) && 
          !((XmlAttribute)element).isNamespaceDeclaration() &&
          JavaFxPsiUtil.isReadOnly(attributeName,  ((XmlAttribute)element).getParent())) {
        holder.createErrorAnnotation(element.getNavigationElement(), "Property '" + attributeName + "' is read-only");
      }
    }
  }

  private static void attachColorIcon(final PsiElement element, AnnotationHolder holder, String attributeValueText) {
    final PsiElement parent = element.getParent();
    if (parent instanceof XmlAttribute) {
      final XmlAttributeDescriptor descriptor = ((XmlAttribute)parent).getDescriptor();
      if (descriptor instanceof JavaFxPropertyAttributeDescriptor) {
        final PsiElement declaration = descriptor.getDeclaration();
        if (declaration instanceof PsiField) {
          final PsiField field = (PsiField)declaration;
          final PsiClassType propertyClassType = JavaFxPsiUtil.getPropertyClassType(field);
          if (propertyClassType != null && InheritanceUtil.isInheritor(propertyClassType, JavaFxCommonClassNames.JAVAFX_SCENE_PAINT)) {
            try {
              final Color color = ColorUtil.fromHex(attributeValueText.substring(1));
              if (color != null) {
                final ColorIcon icon = new ColorIcon(8, color);
                final Annotation annotation = holder.createInfoAnnotation(element, null);
                annotation.setGutterIconRenderer(new ColorIconRenderer(icon, element));
              }
            }
            catch (Exception ignored) {
            }
          }
        }
      }
    }
  }

  private static class ColorIconRenderer extends GutterIconRenderer {
    private final ColorIcon myIcon;
    private final PsiElement myElement;

    public ColorIconRenderer(ColorIcon icon, PsiElement element) {
      myIcon = icon;
      myElement = element;
    }

    @NotNull
    @Override
    public Icon getIcon() {
      return myIcon;
    }

    @Override
    public boolean equals(Object obj) {
      return myElement.equals(obj);
    }

    @Override
    public int hashCode() {
      return myElement.hashCode();
    }

    @Override
    public AnAction getClickAction() {
      return new AnAction() {
        @Override
        public void actionPerformed(AnActionEvent e) {
          final Editor editor = PlatformDataKeys.EDITOR.getData(e.getDataContext());
          if (editor != null) {
            XmlChooseColorIntentionAction.chooseColor(editor.getComponent(), myElement, "Color Chooser", true);
          }
        }
      };
    }
  }
}
