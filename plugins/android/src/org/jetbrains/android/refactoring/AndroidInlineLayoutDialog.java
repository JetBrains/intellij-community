package org.jetbrains.android.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.inline.InlineOptionsDialog;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidInlineLayoutDialog extends InlineOptionsDialog {
  private final XmlFile myLayoutFile;
  private final XmlTag myLayoutRootTag;
  private final PsiElement myUsageElement;

  public AndroidInlineLayoutDialog(@NotNull Project project,
                                   @NotNull XmlFile layoutFile,
                                   @NotNull XmlTag layoutRootTag,
                                   @Nullable PsiElement usageElement) {
    super(project, true, layoutFile);
    myLayoutFile = layoutFile;
    myLayoutRootTag = layoutRootTag;
    myUsageElement = usageElement;
    myInvokedOnReference = usageElement != null;
    setTitle(AndroidBundle.message("android.inline.layout.title"));
    init();
  }

  @Override
  protected String getNameLabelText() {
    return "Layout file '" + myLayoutFile.getName() + "'";
  }

  @Override
  protected String getBorderTitle() {
    return "Inline";
  }

  @Override
  protected String getInlineAllText() {
    return AndroidBundle.message("android.inline.file.inline.all.text");
  }

  @Override
  protected String getInlineThisText() {
    return AndroidBundle.message("android.inline.file.inline.this.text");
  }

  @Override
  protected boolean isInlineThis() {
    return myUsageElement != null;
  }

  @Override
  protected void doAction() {
    final PsiElement usageElement = isInlineThisOnly() ? myUsageElement : null;
    invokeRefactoring(new AndroidInlineLayoutProcessor(myProject, myLayoutFile, myLayoutRootTag, usageElement, null));
  }
}
