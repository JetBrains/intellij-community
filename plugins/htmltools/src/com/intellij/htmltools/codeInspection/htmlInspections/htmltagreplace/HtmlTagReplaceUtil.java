package com.intellij.htmltools.codeInspection.htmlInspections.htmltagreplace;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class HtmlTagReplaceUtil {
  public static XmlFile generateXmlFile(@NotNull Project project, String filetext) {
    return (XmlFile)PsiFileFactory.getInstance(project).createFileFromText("dummy.xml", XMLLanguage.INSTANCE,
                                                                                            filetext);
  }

  public static XmlFile genereateXmlFileWithSingleTag(@NotNull Project project, String tagname) {
    @NonNls String filetext = "<" + tagname + "></" + tagname + ">";
    return generateXmlFile(project, filetext);
  }

  public static PsiElement[] getXmlNamesFromSingleTagFile(XmlFile xmlFile) {
    XmlTag tag = xmlFile.getDocument().getRootTag();
    assert tag != null;
    PsiElement[] answer = new PsiElement[2];
    int cnt = 0;
    for (PsiElement child : tag.getChildren()) {
      if (child instanceof XmlToken) {
        IElementType type = ((XmlToken)child).getTokenType();
        if (type == XmlTokenType.XML_NAME) {
          answer[cnt++] = child;
        }
      }
    }
    return answer;
  }
}
