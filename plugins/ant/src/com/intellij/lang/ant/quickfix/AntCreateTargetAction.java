package com.intellij.lang.ant.quickfix;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.resources.AntBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NotNull;

public class AntCreateTargetAction extends BaseIntentionAction {

  private final AntProject myAntProject;
  private final String myName;
  private final String myFile;

  public AntCreateTargetAction(final AntProject antProject, final String name) {
    this(antProject, name, null);
  }

  public AntCreateTargetAction(final AntProject antProject, final String name, final String file) {
    myAntProject = antProject;
    myName = name;
    myFile = file;
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
      if (myFile != null) {
        builder.append(' ');
        builder.append(AntBundle.getMessage("text.in.the.file", myFile));
      }
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
    ((Navigatable)targetTag).navigate(true);
  }
}
