package org.jetbrains.android.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.dom.converters.AndroidResourceReferenceBase;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.dom.resources.ResourcesDomFileDescription;
import org.jetbrains.android.dom.resources.Style;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.ProjectBasedErrorReporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidInlineStyleReferenceAction extends AndroidBaseXmlRefactoringAction {
  public static final String ACTION_ID = "AndroidInlineStyleReferenceAction";

  private final AndroidInlineTestConfig myTestConfig;

  @TestOnly
  AndroidInlineStyleReferenceAction(AndroidInlineTestConfig testConfig) {
    myTestConfig = testConfig;
  }

  @SuppressWarnings("UnusedDeclaration")
  public AndroidInlineStyleReferenceAction() {
    myTestConfig = null;
  }

  @Override
  protected void doRefactorForTags(@NotNull Project project, @NotNull final XmlTag[] tags) {
    assert tags.length == 1;
    final XmlTag tag = tags[0];

    final PsiFile file = tag.getContainingFile();
    if (file == null) {
      return;
    }
    final StyleUsageData usageData = AndroidInlineUtil.getStyleUsageData(tag);

    if (usageData == null) {
      return;
    }
    final AndroidResourceReferenceBase reference = usageData.getReference();
    final String title = AndroidBundle.message("android.inline.style.title");
    final PsiElement[] styleElements = reference.computeTargetElements();

    if (styleElements.length == 0) {
      AndroidUtils.reportError(project, "Cannot find style by reference '" + reference.getValue() + "'", title);
      return;
    }

    if (styleElements.length > 1) {
      AndroidUtils.reportError(project, RefactoringBundle.getCannotRefactorMessage("Unambiguous style reference."), title);
      return;
    }
    final PsiElement styleElement = styleElements[0];
    final XmlTag styleTag = PsiTreeUtil.getParentOfType(styleElement, XmlTag.class);
    final DomElement domElement = styleTag != null ? DomManager.getDomManager(project).getDomElement(styleTag) : null;

    if (!(domElement instanceof Style)) {
      AndroidUtils.reportError(project, "Cannot find style by reference '" + reference.getValue() + "'", title);
      return;
    }
    final Style style = (Style)domElement;
    String styleName = style.getName().getStringValue();

    if (styleName == null) {
      AndroidUtils.reportError(project, RefactoringBundle.getCannotRefactorMessage("Style name is not specified."), title);
      return;
    }
    AndroidInlineUtil.doInlineStyleDeclaration(project, new AndroidInlineUtil.MyStyleData(styleName, style, styleElement),
                                               usageData,
                                               new ProjectBasedErrorReporter(project), myTestConfig);
  }

  @Override
  protected boolean isEnabledForTags(@NotNull XmlTag[] tags) {
    return tags.length == 1 && AndroidInlineUtil.getStyleUsageData(tags[0]) != null;
  }

  @Override
  protected boolean isMyFile(PsiFile file) {
    final DomFileDescription<?> description = DomManager.getDomManager(file.getProject()).getDomFileDescription((XmlFile)file);

    return description instanceof LayoutDomFileDescription ||
           description instanceof ResourcesDomFileDescription;
  }
}
