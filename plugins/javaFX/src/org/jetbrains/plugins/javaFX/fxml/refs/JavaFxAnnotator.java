// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.codeInsight.intention.AddAnnotationModCommandAction;
import com.intellij.codeInsight.intentions.XmlChooseColorIntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
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
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ui.ColorIcon;
import com.intellij.xml.util.ColorMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.JavaFXBundle;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;
import org.jetbrains.plugins.javaFX.fxml.codeInsight.intentions.JavaFxInjectPageLanguageIntention;
import org.jetbrains.plugins.javaFX.fxml.codeInsight.intentions.JavaFxWrapWithDefineIntention;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxBuiltInTagDescriptor;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public final class JavaFxAnnotator implements Annotator {
  @Override
  public void annotate(final @NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    final PsiFile containingFile = holder.getCurrentAnnotationSession().getFile();
    if (!JavaFxFileTypeFactory.isFxml(containingFile)) return;
    if (element instanceof XmlAttributeValue) {
      final String value = ((XmlAttributeValue)element).getValue();
      if (!JavaFxPsiUtil.isExpressionBinding(value) && !JavaFxPsiUtil.isIncorrectExpressionBinding(value)) {
        final PsiReference[] references = element.getReferences();
        for (PsiReference reference : references) {
          if (reference instanceof JavaFxColorReference) {
            attachColorIcon(element, holder, StringUtil.unquoteString(element.getText()));
            continue;
          }
          final PsiElement resolve = reference.resolve();
          if (resolve instanceof PsiMember member) {
            if (!JavaFxPsiUtil.isVisibleInFxml(member)) {
              final String symbolPresentation = "'" + SymbolPresentationUtil.getSymbolPresentableText(resolve) + "'";
              AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, symbolPresentation +
                                                                                        (resolve instanceof PsiClass
                                                                                         ? JavaFXBundle.message("javafx.annotator.should.be.public")
                                                                                         : JavaFXBundle.message("javafx.annotator.should.be.public.or.fxml.annotated")));
              if (!(resolve instanceof PsiClass)) {
                var fix = new AddAnnotationModCommandAction(JavaFxCommonNames.JAVAFX_FXML_ANNOTATION, member,
                                                            ArrayUtilRt.EMPTY_STRING_ARRAY);
                builder = builder.withFix(fix)
                  .newFix(fix).batch()
                  .registerFix();
              }
              builder.create();
            }
          }
        }
      }
    } else if (element instanceof XmlAttribute attribute) {
      final String attributeName = attribute.getName();
      if (!FxmlConstants.FX_BUILT_IN_ATTRIBUTES.contains(attributeName) &&
          !attribute.isNamespaceDeclaration() &&
          JavaFxPsiUtil.isReadOnly(attributeName, attribute.getParent())) {
        holder.newAnnotation(HighlightSeverity.ERROR, JavaFXBundle.message("javafx.annotator.property.is.read.only", attributeName)).range(element.getNavigationElement()).create();
      }
      if (FxmlConstants.SOURCE.equals(attributeName)) {
        final XmlAttributeValue valueElement = attribute.getValueElement();
        if (valueElement != null) {
          final XmlTag xmlTag = attribute.getParent();
          if (xmlTag != null) {
            final XmlTag referencedTag = JavaFxBuiltInTagDescriptor.getReferencedTag(xmlTag);
            if (referencedTag != null) {
              if (referencedTag.getTextOffset() > xmlTag.getTextOffset()) {
                holder.newAnnotation(HighlightSeverity.ERROR, JavaFXBundle.message("javafx.annotator.value.not.found", valueElement.getValue())).range(valueElement.getValueTextRange()).create();
              } else if (xmlTag.getParentTag() == referencedTag.getParentTag()) {
                holder.newAnnotation(HighlightSeverity.ERROR, JavaFXBundle.message("javafx.annotator.duplicate.child.added")).range(valueElement.getValueTextRange())
                .withFix(new JavaFxWrapWithDefineIntention(referencedTag, valueElement.getValue())).create();
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

              holder.newAnnotation(HighlightSeverity.ERROR, JavaFXBundle.message("javafx.annotator.page.language.not.specified")).range(openTag != null ? openTag.getPsi() : element)
            .withFix(new JavaFxInjectPageLanguageIntention()).create();
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
        final ColorIcon icon = JBUIScale.scaleIcon(new ColorIcon(8, color));
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION).gutterIconRenderer(new ColorIconRenderer(icon, element)).create();
      }
    }
    catch (Exception ignored) {
    }
  }

  private static final class ColorIconRenderer extends GutterIconRenderer implements DumbAware {
    private final ColorIcon myIcon;
    private final PsiElement myElement;

    ColorIconRenderer(ColorIcon icon, PsiElement element) {
      myIcon = icon;
      myElement = element;
    }

    @Override
    public @NotNull Icon getIcon() {
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
        public void actionPerformed(@NotNull AnActionEvent e) {
          final Editor editor = e.getData(CommonDataKeys.EDITOR);
          if (editor != null) {
            XmlChooseColorIntentionAction.chooseColor(editor.getComponent(), myElement);
          }
        }
      };
    }
  }
}
