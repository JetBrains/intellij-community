package org.jetbrains.android.refactoring;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.lang.Language;
import com.intellij.lang.refactoring.InlineActionHandler;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.dom.layout.LayoutViewElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.ErrorReporter;
import org.jetbrains.android.util.HintBasedErrorReporter;
import org.jetbrains.android.util.ProjectBasedErrorReporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidInlineStyleHandler extends InlineActionHandler {
  private static AndroidInlineTestConfig ourTestConfig;

  @TestOnly
  public static void setTestConfig(@Nullable AndroidInlineTestConfig testConfig) {
    ourTestConfig = testConfig;
  }

  @Override
  public boolean isEnabledForLanguage(Language l) {
    return l == XMLLanguage.INSTANCE;
  }

  @Override
  public boolean canInlineElement(PsiElement element) {
    return element != null &&
           AndroidFacet.getInstance(element) != null &&
           element.getManager().isInProject(element) &&
           AndroidInlineUtil.getStyleDataFromContext(element) != null;
  }

  @Override
  public void inlineElement(Project project, final Editor editor, PsiElement element) {
    final AndroidInlineUtil.MyStyleData data = AndroidInlineUtil.getStyleDataFromContext(element);
    if (data != null) {
      final PsiReference reference = editor != null
                                     ? TargetElementUtilBase.findReference(editor)
                                     : null;
      final PsiElement usageElement = reference != null ? reference.getElement() : null;
      final AndroidInlineUtil.MyStyleUsageData usageData = usageElement != null
                                                           ? getStyleUsageDataFromContext(project, usageElement)
                                                           : null;
      final ErrorReporter reporter = editor != null
                                     ? new HintBasedErrorReporter(editor)
                                     : new ProjectBasedErrorReporter(project);
      AndroidInlineUtil.doInlineStyleDeclaration(project, data, usageData, reporter, ourTestConfig);
    }
  }

  @Nullable
  private static AndroidInlineUtil.MyStyleUsageData getStyleUsageDataFromContext(@NotNull Project project, @NotNull PsiElement context) {
    final XmlTag tag = PsiTreeUtil.getParentOfType(context, XmlTag.class, false);

    if (tag == null) {
      return null;
    }
    final DomElement element = DomManager.getDomManager(project).getDomElement(tag);

    if (!(element instanceof LayoutViewElement)) {
      return null;
    }
    final PsiFile file = tag.getContainingFile();

    if (file == null) {
      return null;
    }
    return new AndroidInlineUtil.MyStyleUsageData(tag, ((LayoutViewElement)element).getStyle());
  }
}
