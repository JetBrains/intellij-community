package org.jetbrains.android.inspections.lint;

import com.android.tools.lint.checks.TypographyDetector;
import com.android.tools.lint.detector.api.Issue;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
class TypographyQuickFix implements AndroidLintQuickFix {
  private final Issue myIssue;
  private final String myMessage;

  public TypographyQuickFix(@NotNull Issue issue, @NotNull String message) {
    myIssue = issue;
    myMessage = message;
  }

  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @Nullable Editor editor) {
    final XmlTag tag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class);
    if (tag == null) {
      return;
    }

    for (PsiElement child : tag.getChildren()) {
      if (child instanceof XmlText) {
        final XmlText xmlText = (XmlText)child;
        final String value = xmlText.getValue();

        if (value != null) {
          final List<TypographyDetector.ReplaceEdit> edits = TypographyDetector.getEdits(myIssue.getId(), myMessage, new FakeNode(value));
          final StringBuilder builder = new StringBuilder(value);

          for (TypographyDetector.ReplaceEdit edit : edits) {
            builder.replace(edit.offset, edit.offset + edit.length, edit.replaceWith);
          }

          final String newValue = builder.toString();
          if (!newValue.equals(value)) {
            xmlText.setValue(newValue);
          }
        }
      }
    }
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement, @NotNull PsiElement endElement, boolean inBatchMode) {
    return PsiTreeUtil.getParentOfType(startElement, XmlTag.class) != null;
  }

  @NotNull
  @Override
  public String getName() {
    return AndroidBundle.message("android.lint.inspections.replace.with.suggested.characters");
  }
}
