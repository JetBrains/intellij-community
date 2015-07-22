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

import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.codeInsight.intentions.XmlChooseColorIntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.xml.*;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.ColorIcon;
import com.intellij.xml.util.ColorMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonClassNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;
import org.jetbrains.plugins.javaFX.fxml.codeInsight.intentions.JavaFxInjectPageLanguageIntention;
import org.jetbrains.plugins.javaFX.fxml.codeInsight.intentions.JavaFxWrapWithDefineIntention;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxDefaultPropertyElementDescriptor;

import javax.swing.*;
import java.awt.*;
import java.util.List;

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
      if (references.length == 1 && references[0] instanceof JavaFxColorReference) {
        attachColorIcon(element, holder, StringUtil.stripQuotesAroundValue(element.getText()));
      }
    } else if (element instanceof XmlAttribute) {
      final XmlAttribute attribute = (XmlAttribute)element;
      final String attributeName = attribute.getName();
      if (!FxmlConstants.FX_DEFAULT_PROPERTIES.contains(attributeName) &&
          !attribute.isNamespaceDeclaration() &&
          JavaFxPsiUtil.isReadOnly(attributeName, attribute.getParent())) {
        holder.createErrorAnnotation(element.getNavigationElement(), "Property '" + attributeName + "' is read-only");
      }
      if (FxmlConstants.FX_ELEMENT_SOURCE.equals(attributeName)) {
        final XmlAttributeValue valueElement = attribute.getValueElement();
        if (valueElement != null) {
          final XmlTag xmlTag = attribute.getParent();
          if (xmlTag != null) {
            final XmlTag referencedTag = JavaFxDefaultPropertyElementDescriptor.getReferencedTag(xmlTag);
            if (referencedTag != null) {
              if (referencedTag.getTextOffset() > xmlTag.getTextOffset()) {
                holder.createErrorAnnotation(valueElement.getValueTextRange(), valueElement.getValue() + " not found");
              } else if (xmlTag.getParentTag() == referencedTag.getParentTag()) {
                final Annotation annotation = holder.createErrorAnnotation(valueElement.getValueTextRange(), "Duplicate child added");
                annotation.registerFix(new JavaFxWrapWithDefineIntention(referencedTag, valueElement.getValue()));
              }
            }
          }
        }
      }
    }
    else if (element instanceof XmlTag) {
      if (FxmlConstants.FX_SCRIPT.equals(((XmlTag)element).getName())) {
        final XmlTagValue tagValue = ((XmlTag)element).getValue();
        if (!StringUtil.isEmptyOrSpaces(tagValue.getText())) {
          final List<String> langs = JavaFxPsiUtil.parseInjectedLanguages((XmlFile)element.getContainingFile());
          if (langs.isEmpty()) {
            final ASTNode openTag = element.getNode().findChildByType(XmlTokenType.XML_NAME);
            final Annotation annotation =
              holder.createErrorAnnotation(openTag != null ? openTag.getPsi() : element, "Page language not specified.");
            annotation.registerFix(new JavaFxInjectPageLanguageIntention());
          }
        }
      }
    }
  }

  private static void attachColorIcon(final PsiElement element, AnnotationHolder holder, String attributeValueText) {
    try {
      Color color = null;
      if (attributeValueText.startsWith("#")) {
        color = ColorUtil.fromHex(attributeValueText.substring(1));
      } else {
        final String hexCode = ColorMap.getHexCodeForColorName(StringUtil.toLowerCase(attributeValueText));
        if (hexCode != null) {
          color = ColorUtil.fromHex(hexCode);
        }
      }
      if (color != null) {
        final ColorIcon icon = new ColorIcon(8, color);
        final Annotation annotation = holder.createInfoAnnotation(element, null);
        annotation.setGutterIconRenderer(new ColorIconRenderer(icon, element));
      }
    }
    catch (Exception ignored) {
    }
  }

  private static class ColorIconRenderer extends GutterIconRenderer implements DumbAware {
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
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ColorIconRenderer renderer = (ColorIconRenderer)o;

      if (myElement != null ? !myElement.equals(renderer.myElement) : renderer.myElement != null) return false;

      return true;
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
          final Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
          if (editor != null) {
            XmlChooseColorIntentionAction.chooseColor(editor.getComponent(), myElement, "Color Chooser", true);
          }
        }
      };
    }
  }
}
