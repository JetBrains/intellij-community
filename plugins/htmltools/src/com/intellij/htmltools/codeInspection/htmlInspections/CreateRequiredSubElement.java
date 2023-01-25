package com.intellij.htmltools.codeInspection.htmlInspections;

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.htmltools.HtmlToolsBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CreateRequiredSubElement extends LocalQuickFixAndIntentionActionOnPsiElement {

  private final String myName;

  protected CreateRequiredSubElement(@Nullable PsiElement element, String name) {
    super(element);
    myName = name;
  }

  @NotNull
  @Override
  public String getText() {
    return HtmlToolsBundle.message("html.intention.create.sub.element.text", myName);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return HtmlToolsBundle.message("html.intention.create.sub.element.family", myName);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    XmlTag tag = ((XmlTag)startElement);
    XmlTag title = tag.createChildTag(myName, tag.getNamespace(), "", false);
    XmlTag createdTag = tag.addSubTag(title, false);
    //Fix all such problems in file (project)
    if (editor == null) return;
    editor.getCaretModel().moveToOffset(createdTag.getTextOffset() + title.getTextLength() / 2);
  }

}
