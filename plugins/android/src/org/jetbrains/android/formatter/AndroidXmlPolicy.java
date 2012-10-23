package org.jetbrains.android.formatter;

import com.intellij.formatting.FormattingDocumentModel;
import com.intellij.formatting.WrapType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.formatter.xml.XmlPolicy;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author Eugene.Kudelevsky
*/
public class AndroidXmlPolicy extends XmlPolicy {
  private final AndroidXmlCodeStyleSettings.MySettings myCustomSettings;

  public AndroidXmlPolicy(CodeStyleSettings settings,
                          AndroidXmlCodeStyleSettings.MySettings customSettings,
                          FormattingDocumentModel documentModel) {
    super(settings, documentModel);
    myCustomSettings = customSettings;
  }

  @Override
  public WrapType getWrappingTypeForTagBegin(XmlTag tag) {
    final PsiElement element = getNextSiblingElement(tag);

    if (element instanceof XmlTag && insertLineBreakBeforeTag((XmlTag)element)) {
      return WrapType.NORMAL;
    }
    return super.getWrappingTypeForTagBegin(tag);
  }

  @Override
  public int getAttributesWrap() {
    return myCustomSettings.WRAP_ATTRIBUTES;
  }

  @Override
  public boolean insertLineBreakBeforeFirstAttribute(XmlAttribute attribute) {
    if (!myCustomSettings.INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE ||
        attribute.isNamespaceDeclaration()) {
      return false;
    }
    return attribute.getParent().getAttributes().length > 1;
  }

  @Nullable
  protected static PsiElement getPrevSiblingElement(@NotNull PsiElement element) {
    final PsiElement prev = element.getPrevSibling();
    ASTNode prevNode = SourceTreeToPsiMap.psiElementToTree(prev);

    while (prevNode != null && FormatterUtil.containsWhiteSpacesOnly(prevNode)) {
      prevNode = prevNode.getTreePrev();
    }
    return SourceTreeToPsiMap.treeElementToPsi(prevNode);
  }

  @Nullable
  protected static PsiElement getNextSiblingElement(@NotNull PsiElement element) {
    final PsiElement next = element.getNextSibling();
    ASTNode nextNode = SourceTreeToPsiMap.psiElementToTree(next);

    while (nextNode != null && FormatterUtil.containsWhiteSpacesOnly(nextNode)) {
      nextNode = nextNode.getTreeNext();
    }
    return SourceTreeToPsiMap.treeElementToPsi(nextNode);
  }
}
