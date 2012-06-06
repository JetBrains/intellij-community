package org.jetbrains.android.inspections.lint;

import com.android.tools.lint.checks.TypographyDetector;
import com.android.tools.lint.detector.api.Issue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

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
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
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
            String with = edit.replaceWith;

            if (ApplicationManager.getApplication().isUnitTestMode()) {
              with = with.replace('\u2013', '~').replace('\u2018', '{').replace('\u2019', '}');
            }

            builder.replace(edit.offset, edit.offset + edit.length, with);
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
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    return PsiTreeUtil.getParentOfType(startElement, XmlTag.class) != null;
  }

  @NotNull
  @Override
  public String getName() {
    return AndroidBundle.message("android.lint.inspections.replace.with.suggested.characters");
  }
}
