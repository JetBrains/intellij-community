package org.jetbrains.android.refactoring;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.lang.Language;
import com.intellij.lang.refactoring.InlineActionHandler;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.converters.AndroidResourceReferenceBase;
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
           AndroidInlineUtil.getInlinableStyleDataFromContext(element) != null;
  }

  @Override
  public void inlineElement(Project project, final Editor editor, PsiElement element) {
    final AndroidInlineUtil.MyStyleData data = AndroidInlineUtil.getInlinableStyleDataFromContext(element);

    if (data != null) {
      final ErrorReporter reporter = editor != null
                                     ? new HintBasedErrorReporter(editor)
                                     : new ProjectBasedErrorReporter(project);
      StyleUsageData usageData = null;

      if (editor != null) {
        final PsiReference reference = TargetElementUtilBase.findReference(editor);

        if (reference instanceof AndroidResourceReferenceBase) {
          usageData = getUsageDataFromEditor(reference);
        }
      }
      AndroidInlineUtil.doInlineStyleDeclaration(project, data, usageData, reporter, ourTestConfig);
    }
  }

  @Nullable
  private static StyleUsageData getUsageDataFromEditor(@NotNull PsiReference reference) {
    final PsiElement usageElement = reference.getElement();

    if (usageElement == null) {
      return null;
    }
    final XmlTag tag = PsiTreeUtil.getParentOfType(usageElement, XmlTag.class, false);
    return tag != null ? AndroidInlineUtil.getStyleUsageData(tag) : null;
  }
}
