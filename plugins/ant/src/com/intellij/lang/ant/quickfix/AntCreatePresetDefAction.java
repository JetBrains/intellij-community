package com.intellij.lang.ant.quickfix;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.misc.AntPsiUtil;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NotNull;

public class AntCreatePresetDefAction extends BaseIntentionAction {

  private final AntStructuredElement myUndefinedElement;
  private final AntFile myFile;

  public AntCreatePresetDefAction(final AntStructuredElement undefinedElement) {
    this(undefinedElement, null);
  }

  public AntCreatePresetDefAction(final AntStructuredElement undefinedElement, final AntFile file) {
    myUndefinedElement = undefinedElement;
    myFile = file;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  @NotNull
  public String getFamilyName() {
    final String i18nName = AntBundle.message("intention.create.presetdef.family.name");
    return (i18nName == null) ? "Create presetdef" : i18nName;
  }

  @NotNull
  public String getText() {
    StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append(getFamilyName());
      builder.append(" '");
      builder.append(myUndefinedElement.getSourceElement().getName());
      builder.append('\'');
      if (myFile != null) {
        builder.append(' ');
        builder.append(AntBundle.message("text.in.the.file", myFile.getName()));
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
    final AntStructuredElement element = myUndefinedElement;
    final AntProject antProject = (myFile == null) ? element.getAntProject() : myFile.getAntProject();
    final AntElement anchor =
      (myFile == null) ? AntPsiUtil.getSubProjectElement(element) : PsiTreeUtil.getChildOfType(antProject, AntStructuredElement.class);
    final XmlTag se = element.getSourceElement();
    final XmlTag projectTag = antProject.getSourceElement();

    // create presetdef tag
    XmlTag presetDef = projectTag.createChildTag("presetdef", projectTag.getNamespace(), null, false);
    presetDef.setAttribute("name", se.getName());

    // insert presetdef in file and navigate to it
    presetDef = (XmlTag)((anchor == null) ? projectTag.add(presetDef) : projectTag.addBefore(presetDef, anchor.getSourceElement()));
    ((Navigatable)presetDef).navigate(true);

    // if presetdef is inserted into an imported file, clear caches in order to re-annotate current element
    if (myFile != null) {
      element.getAntFile().clearCaches();
    }
  }
}
