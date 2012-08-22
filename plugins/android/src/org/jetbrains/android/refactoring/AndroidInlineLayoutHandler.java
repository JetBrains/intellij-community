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
import com.intellij.psi.xml.*;
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
    if (element instanceof ResourceElementWrapper) {
      element = ((ResourceElementWrapper)element).getWrappee();
    }
    if (!(element instanceof XmlElement)) {
      return false;
    }
    if (AndroidFacet.getInstance(element) == null) {
      return false;
    }
    if (element instanceof XmlFile) {
      if (((XmlFile)element).getRootTag() == null) {
        return false;
      }
      return DomManager.getDomManager(element.getProject()).getDomFileDescription((XmlFile)element)
        instanceof LayoutDomFileDescription;
    }
    else if (element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_NAME) {
      return getLayoutUsageDataFromContext(element) != null;
    }
    return false;
  }

  @Nullable
  private static LayoutUsageData getLayoutUsageDataFromContext(PsiElement context) {
    final XmlTag tag = PsiTreeUtil.getParentOfType(context, XmlTag.class);
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
    final LayoutUsageData usageData = getLayoutUsageDataFromContext(element);
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
    AndroidInlineUtil.doInlineLayoutFile(project, (XmlFile)resolvedElement, usageData.getIncludeTag(), ourTestConfig);
  }
}
