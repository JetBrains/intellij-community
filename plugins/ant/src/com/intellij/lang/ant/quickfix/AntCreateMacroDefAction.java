package com.intellij.lang.ant.quickfix;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.lang.ant.misc.AntPsiUtil;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.resources.AntBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NotNull;

public class AntCreateMacroDefAction extends BaseIntentionAction {

  private final AntStructuredElement myUndefinedElement;
  private final AntFile myFile;

  public AntCreateMacroDefAction(final AntStructuredElement undefinedElement) {
    this(undefinedElement, null);
  }

  public AntCreateMacroDefAction(final AntStructuredElement undefinedElement, final AntFile file) {
    myUndefinedElement = undefinedElement;
    myFile = file;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  @NotNull
  public String getFamilyName() {
    final String i18nName = AntBundle.getMessage("intention.create.macrodef.family.name");
    return (i18nName == null) ? "Create macrodef" : i18nName;
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
        builder.append(AntBundle.getMessage("text.in.the.file", myFile.getName()));
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

    // create macrodef tag
    XmlTag macrodefTag = projectTag.createChildTag("macrodef", projectTag.getNamespace(), null, false);
    macrodefTag.setAttribute("name", se.getName());

    // create attribute definitons
    for (XmlAttribute attr : se.getAttributes()) {
      XmlTag attrTag = macrodefTag.createChildTag("attribute", macrodefTag.getNamespace(), null, false);
      attrTag.setAttribute("name", attr.getName());
      macrodefTag.add(attrTag);
    }

    // create definitons of nested elements
    for (XmlTag subtag : se.getSubTags()) {
      XmlTag elementTag = macrodefTag.createChildTag("element", macrodefTag.getNamespace(), null, false);
      elementTag.setAttribute("name", subtag.getName());
      macrodefTag.add(elementTag);
    }

    // insert macrodef in file and navigate to it
    macrodefTag = (XmlTag)((anchor == null) ? projectTag.add(macrodefTag) : projectTag.addBefore(macrodefTag, anchor.getSourceElement()));
    ((Navigatable)macrodefTag).navigate(true);

    // if macrodef is inserted into an imported file, clear caches in order to re-annotate current element
    if (myFile != null) {
      element.getAntFile().clearCaches();
    }
  }
}

