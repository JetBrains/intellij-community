package org.jetbrains.android.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.android.dom.converters.AndroidResourceReference;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.dom.resources.Style;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.ProjectBasedErrorReporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidInlineStyleReferenceAction extends AndroidBaseLayoutRefactoringAction {
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
  protected void doRefactor(@NotNull Project project, @NotNull final XmlTag tag) {
    final PsiFile file = tag.getContainingFile();
    if (file == null) {
      return;
    }
    final Pair<AndroidResourceReference, GenericAttributeValue<ResourceValue>> pair = AndroidInlineUtil
      .findStyleReference(tag);

    if (pair == null) {
      return;
    }
    final AndroidResourceReference reference = pair.getFirst();
    final GenericAttributeValue<ResourceValue> styleAttribute = pair.getSecond();

    if (reference == null || styleAttribute == null) {
      return;
    }
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
                                               new AndroidInlineUtil.MyStyleUsageData(file, tag, styleAttribute),
                                               new ProjectBasedErrorReporter(project), myTestConfig);
  }

  @Override
  protected boolean isEnabled(@NotNull XmlTag tag) {
    return super.isEnabled(tag) &&
           AndroidInlineUtil.findStyleReference(tag) != null;
  }
}
