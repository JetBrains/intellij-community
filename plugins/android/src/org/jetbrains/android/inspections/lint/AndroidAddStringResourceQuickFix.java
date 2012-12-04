package org.jetbrains.android.inspections.lint;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.android.intentions.AndroidAddStringResourceAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidAddStringResourceQuickFix extends AndroidAddStringResourceAction {
  private final PsiElement myStartElement;

  public AndroidAddStringResourceQuickFix(@NotNull PsiElement startElement) {
    myStartElement = startElement;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final XmlAttributeValue value = getAttributeValue(myStartElement);
    return value != null && getStringLiteralValue(value, file) != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    invokeIntention(project, editor, file, null);
  }

  public void invokeIntention(Project project, Editor editor, PsiFile file, String resName) {
    final XmlAttributeValue attributeValue = getAttributeValue(myStartElement);
    if (attributeValue != null) {
      doInvoke(project, editor, file, resName, attributeValue);
    }
  }

  @Nullable
  private static XmlAttributeValue getAttributeValue(@NotNull PsiElement element) {
    final XmlAttribute attribute = PsiTreeUtil.getParentOfType(element, XmlAttribute.class);
    return attribute != null ? attribute.getValueElement() : null;
  }
}
