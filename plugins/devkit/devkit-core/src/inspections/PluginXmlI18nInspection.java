// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.highlighting.BasicDomElementsInspection;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomHighlightingHelper;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Action;
import org.jetbrains.idea.devkit.dom.ActionOrGroup;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

public class PluginXmlI18nInspection extends BasicDomElementsInspection<IdeaPlugin> {
  public PluginXmlI18nInspection() {
    super(IdeaPlugin.class);
  }

  @Override
  protected void checkDomElement(DomElement element, DomElementAnnotationHolder holder, DomHighlightingHelper helper) {
    if (element instanceof ActionOrGroup) {
      highlightAction(holder, (ActionOrGroup)element);
    }
  }

  private static void highlightAction(DomElementAnnotationHolder holder, ActionOrGroup action) {
    String id = action.getId().getStringValue();
    if (id == null) return;

    String text = action.getText().getStringValue();
    String desc = action.getDescription().getStringValue();
    if (text == null && desc == null) return;

    holder.createProblem(action, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                         getText(),
                         null, createAnalyzeEPFix(action, id, text, desc));
  }

  @NotNull
  private static String getText() {
    return "Extract text/description for i18n";
  }

  private static LocalQuickFix createAnalyzeEPFix(ActionOrGroup ag, String id, String text, String desc) {
    return new IntentionAndQuickFixAction() {
      @Nls(capitalization = Nls.Capitalization.Sentence)
      @NotNull
      @Override
      public String getName() {
        return PluginXmlI18nInspection.getText();
      }

      @Nls(capitalization = Nls.Capitalization.Sentence)
      @NotNull
      @Override
      public String getFamilyName() {
        return PluginXmlI18nInspection.getText();
      }

      @Override
      public void applyFix(@NotNull Project project, PsiFile file, @Nullable Editor editor) {
        XmlElement xml = ag.getXmlElement();
        if (xml == null) return;
        String prefix = ag instanceof Action ? "action" : "group";

        if (text != null) ag.getText().setStringValue(null);
        if (desc != null) ag.getDescription().setStringValue(null);

        if (text != null) append(project, file, prefix + "." + id + ".text=" + text);
        if (desc != null) append(project, file, prefix + "." + id + ".description=" + desc);

        removeEmptyLines(xml);
      }

      private void removeEmptyLines(XmlElement xml) {
        xml.processElements(element -> {
          if (element instanceof PsiWhiteSpace && element.textContains('\n')) {
            PsiElement next = element.getNextSibling();
            if (next instanceof LeafPsiElement) {
              IElementType type = ((LeafPsiElement)next).getElementType();
              if (type == XmlTokenType.XML_TAG_END || type == XmlTokenType.XML_EMPTY_ELEMENT_END) {
                element.delete();
                return false;
              }
            }
          }
          return true;
        }, xml.getFirstChild());
      }

      private void append(@NotNull Project project, PsiFile file, String tt) {
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (document == null) return;
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
        int length = document.getTextLength();
        document.insertString(length, "\n" + tt);
        PsiDocumentManager.getInstance(project).commitDocument(document);
      }
    };
  }
}
