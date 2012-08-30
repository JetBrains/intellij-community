package org.jetbrains.android.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.inline.InlineOptionsDialog;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
class AndroidInlineStyleDialog extends InlineOptionsDialog {
  private final String myStyleName;
  private final boolean myInlineThisByDefault;
  private final Map<AndroidAttributeInfo, String> myAttributeValues;
  private final StyleRefData myParentStyleRef;
  private final XmlTag myStyleTag;


  public AndroidInlineStyleDialog(@NotNull Project project,
                                  @NotNull PsiElement styleElement,
                                  @NotNull XmlTag styleTag,
                                  @NotNull String styleName,
                                  @NotNull Map<AndroidAttributeInfo, String> attributeValues,
                                  @Nullable StyleRefData parentStyleRef,
                                  boolean inlineThisByDefault,
                                  boolean invokedOnReference) {
    super(project, true, styleElement);
    myStyleTag = styleTag;
    myStyleName = styleName;
    myInlineThisByDefault = inlineThisByDefault;
    myAttributeValues = attributeValues;
    myParentStyleRef = parentStyleRef;
    myInvokedOnReference = invokedOnReference;
    setTitle(AndroidBundle.message("android.inline.style.title", styleName));
    init();
  }

  @Override
  protected String getNameLabelText() {
    return "Style '" + myStyleName + "'";
  }

  @Override
  protected String getBorderTitle() {
    return "Inline";
  }

  @Override
  protected String getInlineAllText() {
    return AndroidBundle.message("android.inline.style.inline.all.text");
  }

  @Override
  protected String getInlineThisText() {
    return AndroidBundle.message("android.inline.style.inline.this.text");
  }

  @Override
  protected boolean isInlineThis() {
    return myInlineThisByDefault;
  }

  @Override
  protected void doAction() {
    if (isInlineThisOnly()) {
      close(OK_EXIT_CODE);
    }
    else {
      invokeRefactoring(new AndroidInlineAllStyleUsagesProcessor(myProject, myElement, myStyleTag, myStyleName,
                                                                 myAttributeValues, myParentStyleRef, null));
    }
  }
}
