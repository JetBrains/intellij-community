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
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.dom.converters.AndroidResourceReferenceBase;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.dom.wrappers.ResourceElementWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.ErrorReporter;
import org.jetbrains.android.util.HintBasedErrorReporter;
import org.jetbrains.android.util.ProjectBasedErrorReporter;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidInlineLayoutHandler extends InlineActionHandler {
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
    return false;
  }

  @Override
  public boolean canInlineElementInEditor(PsiElement element, Editor editor) {
    if (element instanceof ResourceElementWrapper) {
      element = ((ResourceElementWrapper)element).getWrappee();
    }
    if (element instanceof XmlFile) {
      if (AndroidFacet.getInstance(element) == null ||
          ((XmlFile)element).getRootTag() == null) {
        return false;
      }
      return DomManager.getDomManager(element.getProject()).getDomFileDescription((XmlFile)element)
        instanceof LayoutDomFileDescription;
    }
    return getLayoutUsageDataFromContext(editor) != null;
  }

  @Nullable
  private static LayoutUsageData getLayoutUsageDataFromContext(Editor editor) {
    if (editor == null) {
      return null;
    }
    final PsiElement element = PsiUtilBase.getElementAtCaret(editor);

    if (!(element instanceof XmlToken) ||
        AndroidFacet.getInstance(element) == null) {
      return null;
    }
    final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    return tag != null
           ? AndroidInlineUtil.getLayoutUsageData(tag)
           : null;
  }

  @Override
  public void inlineElement(Project project, Editor editor, PsiElement element) {
    if (element instanceof ResourceElementWrapper) {
      element = ((ResourceElementWrapper)element).getWrappee();
    }

    if (element instanceof XmlFile) {
      PsiElement usageElement = null;

      if (editor != null) {
        final PsiReference reference = TargetElementUtilBase.findReference(editor);

        if (reference != null) {
          usageElement = reference.getElement();
        }
      }
      AndroidInlineUtil.doInlineLayoutFile(project, (XmlFile)element, usageElement, ourTestConfig);
      return;
    }
    final LayoutUsageData usageData = getLayoutUsageDataFromContext(editor);
    assert usageData != null;
    final AndroidResourceReferenceBase ref = usageData.getReference();
    final PsiElement[] elements = ref.computeTargetElements();
    final ErrorReporter errorReporter = editor != null
                                        ? new HintBasedErrorReporter(editor)
                                        : new ProjectBasedErrorReporter(project);
    final String title = AndroidBundle.message("android.inline.layout.title");

    if (elements.length == 0) {
      final String resName = ref.getResourceValue().getResourceName();
      final String message = resName != null
                             ? "Cannot find layout '" + resName + "'"
                             : "Error: cannot find the layout";
      errorReporter.report(message, title);
      return;
    }

    if (elements.length > 1) {
      errorReporter.report("Error: unambiguous reference", title);
      return;
    }

    final PsiElement resolvedElement = elements[0];
    if (!(resolvedElement instanceof XmlFile)) {
      errorReporter.report("Cannot inline reference '" + ref.getValue() + "'", title);
      return;
    }
    AndroidInlineUtil.doInlineLayoutFile(project, (XmlFile)resolvedElement, ref.getElement(), ourTestConfig);
  }
}
