package org.jetbrains.android.inspections.lint;

import com.android.SdkConstants;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.SystemResourceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
class SetAttributeQuickFix implements AndroidLintQuickFix {

  private final String myName;
  private final String myAttributeName;
  private final String myValue;

  // 'null' value means asking
  SetAttributeQuickFix(@NotNull String name, @NotNull String attributeName, @Nullable String value) {
    super();
    myName = name;
    myAttributeName = attributeName;
    myValue = value;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    final XmlTag tag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class, false);

    if (tag == null) {
      return;
    }
    String value = myValue;

    if (value == null && context instanceof AndroidQuickfixContexts.DesignerContext) {
      value = askForAttributeValue(tag);
      if (value == null) {
        return;
      }
    }
    final XmlAttribute attribute = tag.setAttribute(myAttributeName, SdkConstants.NS_RESOURCES, "");

    if (attribute != null) {
      if (value != null) {
        attribute.setValue(value);
      }
      if (context instanceof AndroidQuickfixContexts.EditorContext) {
        final Editor editor = ((AndroidQuickfixContexts.EditorContext)context).getEditor();
        final XmlAttributeValue valueElement = attribute.getValueElement();
        final TextRange valueTextRange = attribute.getValueTextRange();

        if (valueElement != null && valueTextRange != null) {
          final int valueElementStart = valueElement.getTextRange().getStartOffset();
          editor.getCaretModel().moveToOffset(valueElementStart + valueTextRange.getStartOffset());

          if (valueTextRange.getStartOffset() < valueTextRange.getEndOffset()) {
            editor.getSelectionModel().setSelection(valueElementStart + valueTextRange.getStartOffset(),
                                                    valueElementStart + valueTextRange.getEndOffset());
          }
        }
      }
    }
  }

  @Nullable
  private String askForAttributeValue(@NotNull PsiElement context) {
    final AndroidFacet facet = AndroidFacet.getInstance(context);
    final String message = "Specify value of attribute '" + myAttributeName + "'";
    final String title = "Set Attribute Value";

    if (facet != null) {
      final SystemResourceManager srm = facet.getSystemResourceManager();

      if (srm != null) {
        final AttributeDefinitions attrDefs = srm.getAttributeDefinitions();

        if (attrDefs != null) {
          final AttributeDefinition def = attrDefs.getAttrDefByName(myAttributeName);
          if (def != null) {
            final String[] variants = def.getValues();

            if (variants.length > 0) {
              return Messages.showEditableChooseDialog(message, title, Messages.getQuestionIcon(), variants, variants[0], null);
            }
          }
        }
      }
    }
    return Messages.showInputDialog(context.getProject(), message, title, Messages.getQuestionIcon());
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    if (myValue == null && contextType == AndroidQuickfixContexts.BatchContext.TYPE) {
      return false;
    }
    final XmlTag tag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class, false);
    if (tag == null) {
      return false;
    }
    return tag.getAttribute(myAttributeName, SdkConstants.NS_RESOURCES) == null;
  }
}
