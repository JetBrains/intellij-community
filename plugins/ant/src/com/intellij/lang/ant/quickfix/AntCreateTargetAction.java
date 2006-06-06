package com.intellij.lang.ant.quickfix;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.resources.AntBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NotNull;

public class AntCreateTargetAction extends BaseIntentionAction {

  private final AntProject myAntProject;
  private final String myName;

  public AntCreateTargetAction(final AntProject antProject, final String name) {
    myAntProject = antProject;
    myName = name;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  @NotNull
  public String getFamilyName() {
    final String i18nName = AntBundle.getMessage("intention.create.target.family.name");
    return (i18nName == null) ? "Create Target" : i18nName;
  }

  @NotNull
  public String getText() {
    StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append(getFamilyName());
      builder.append(" '");
      builder.append(myName);
      builder.append('\'');
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return true;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final XmlTag projectTag = myAntProject.getSourceElement();
    XmlTag targetTag = projectTag.createChildTag("target", projectTag.getNamespace(), null, false);
    targetTag.setAttribute("name", myName);
    targetTag = (XmlTag)projectTag.add(targetTag);
    editor.getCaretModel().moveToOffset(targetTag.getTextOffset() + targetTag.getTextLength() - 2);
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
  }
}
