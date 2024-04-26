package com.intellij.htmltools.codeInspection.htmlInspections.htmlAddLabelToForm;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.htmltools.HtmlToolsBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CreateNewLabelAction implements LocalQuickFix, HighPriorityAction {
  private final String myName;

  public CreateNewLabelAction(String name) {
    myName = name;
  }

  @Override
  public @NotNull String getFamilyName() {
    return HtmlToolsBundle.message("html.inspections.create.new.label", myName);
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    XmlTag myTag = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), XmlTag.class);
    if (myTag == null) return;
    XmlAttributeValue id = findIdValue(myTag);

    if (id != null) {
      PsiElement added = myTag.getParent().addBefore(createLabelFor(myTag, id), myTag);
      XmlToken token = XmlUtil.getTokenOfType(added, XmlTokenType.XML_END_TAG_START);
      if (token != null && token.isPhysical()) {
        PsiNavigateUtil.navigate(token);
      }
    }
    else {
      String text = "<label></label>";
      XmlTag tag = createElementFromText(project, myTag, text);
      tag.addSubTag(myTag, true);
      tag.addBefore(createNewline(project), tag.getSubTags()[0]);
      tag.addAfter(createNewline(project), tag.getSubTags()[0]);
      myTag.getParent().addBefore(tag, myTag);
      myTag.delete();
    }
  }

  private static @NotNull XmlTag createLabelFor(@NotNull XmlTag place, @NotNull XmlAttributeValue id) {
    XmlTag tag = createElementFromText(place.getProject(), place, "<label for=\"x\"></label>");
    //noinspection ConstantConditions
    tag.getAttribute("for").getValueElement().replace(id);
    return tag;
  }

  private static XmlText createNewline(@NotNull Project project) {
    return XmlElementFactory.getInstance(project).createTagFromText("<a>\n</a>").getValue().getTextElements()[0];
  }

  static @Nullable String getId(XmlTag tag) {
    XmlAttributeValue value = findIdValue(tag);
    return value == null ? null : value.getValue();
  }

  private static @Nullable XmlAttributeValue findIdValue(XmlTag tag) {
    for (XmlAttribute attribute : tag.getAttributes()) {
      if (attribute.getName().equalsIgnoreCase("id")) {
        return attribute.getValueElement();
      }
    }
    return null;
  }

  static @NotNull XmlTag createElementFromText(Project project, XmlTag myTag, String text) {
    return myTag instanceof HtmlTag ?
           XmlElementFactory.getInstance(project).createHTMLTagFromText(text) :
           XmlElementFactory.getInstance(project).createXHTMLTagFromText(text);
  }
}
