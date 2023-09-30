package com.intellij.htmltools.codeInspection.htmlInspections.htmlAddLabelToForm;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.htmltools.HtmlToolsBundle;
import com.intellij.htmltools.codeInspection.htmlInspections.HtmlFormInputWithoutLabelInspection;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class CreateLabelFromTextAction implements LocalQuickFix, HighPriorityAction {
  private final String myBundleKey;
  private final boolean myTextBeforeTag;
  private final String myName;

  public CreateLabelFromTextAction(@NonNls @PropertyKey(resourceBundle = HtmlToolsBundle.BUNDLE) String bundleKey,
                                   boolean textBeforeTag, String name) {
    myBundleKey = bundleKey;
    myTextBeforeTag = textBeforeTag;
    myName = name;
  }

  @Override
  public @NotNull String getFamilyName() {
    return HtmlToolsBundle.message(myBundleKey, myName);
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement e = descriptor.getPsiElement();
    if (e == null) return;

    XmlTag myTag = PsiTreeUtil.getParentOfType(e, XmlTag.class);
    if (myTag == null) return;
    Pair<PsiElement, PsiElement> pair = HtmlFormInputWithoutLabelInspection.getNearestText(myTag, myTextBeforeTag ? new HtmlFormInputWithoutLabelInspection.BackwardIterator() : new HtmlFormInputWithoutLabelInspection.ForwardIterator());
    if (pair == null) return;
    final PsiElement myLeft = pair.first;
    final PsiElement myRight = pair.second;

    StringBuilder sb = new StringBuilder("<a>");
    if (myTextBeforeTag) {
      for (PsiElement element = myLeft.getParent().getFirstChild();
           element != null && element != myLeft;
           element = element.getNextSibling()) {
        sb.append(element.getText());
      }
    }
    else {
      for (PsiElement element = myRight.getNextSibling();
           element != null;
           element = element.getNextSibling()) {
        sb.append(element.getText());
        if (element == element.getParent().getLastChild()) {
          break;
        }
      }
    }
    XmlTag tmpTag = CreateNewLabelAction.createElementFromText(project, myTag, sb.toString());
    PsiElement anchor = null;
    for (PsiElement element : tmpTag.getChildren()) {
      if (element instanceof XmlText || element instanceof PsiComment) {
        if (anchor == null) {
          anchor = myLeft.getParent().replace(element);
        } else {
          anchor = myLeft.addAfter(element, anchor);
        }
      }
    }

    String id = CreateNewLabelAction.getId(myTag);

    if (id != null) {
      StringBuilder builder = new StringBuilder("\n<label for=\"").append(id).append("\">");
      for (PsiElement element = myLeft; element != null; element = element.getNextSibling()) {
        builder.append(element.getText());
        if (element == myRight) {
          break;
        }
      }
      builder.append("</label>");
      XmlTag labelTag = CreateNewLabelAction.createElementFromText(project, myTag, builder.toString());
      if (myTextBeforeTag) {
        myTag.getParent().addBefore(labelTag, myTag);
      }
      else {
        myTag.getParent().addAfter(labelTag, myTag);
      }
    }
    else {
      StringBuilder builder = new StringBuilder("\n<label>\n");
      if (!myTextBeforeTag) {
        builder.append(myTag.getText());
        builder.append("\n");
      }
      for (PsiElement element = myLeft; element != null; element = element.getNextSibling()) {
        builder.append(element.getText());
        if (element == myRight) {
          break;
        }
      }
      if (myTextBeforeTag) {
        builder.append("\n");
        builder.append(myTag.getText());
      }
      builder.append("\n</label>");
      XmlTag tag = CreateNewLabelAction.createElementFromText(project, myTag, builder.toString());
      myTag.replace(tag);
    }
  }
}
