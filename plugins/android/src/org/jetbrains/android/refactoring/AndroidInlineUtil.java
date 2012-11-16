package org.jetbrains.android.refactoring;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.converters.AndroidResourceReferenceBase;
import org.jetbrains.android.dom.layout.Include;
import org.jetbrains.android.dom.layout.LayoutViewElement;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.dom.resources.Style;
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.ErrorReporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
class AndroidInlineUtil {
  private AndroidInlineUtil() {
  }

  @Nullable
  static MyStyleData getInlinableStyleData(@NotNull XmlTag tag) {
    final DomElement domElement = DomManager.getDomManager(tag.getProject()).getDomElement(tag);

    if (!(domElement instanceof Style)) {
      return null;
    }
    final Style style = (Style)domElement;
    final XmlAttributeValue nameAttrValue = style.getName().getXmlAttributeValue();

    if (nameAttrValue == null) {
      return null;
    }
    final String styleName = style.getName().getStringValue();

    if (styleName == null || styleName.length() == 0) {
      return null;
    }
    return new MyStyleData(styleName, style, nameAttrValue);
  }

  @Nullable
  static StyleUsageData getStyleUsageData(@NotNull XmlTag tag) {
    final DomElement domElement = DomManager.getDomManager(tag.getProject()).getDomElement(tag);

    if (domElement instanceof LayoutViewElement) {
      final GenericAttributeValue<ResourceValue> styleAttribute = ((LayoutViewElement)domElement).getStyle();
      final AndroidResourceReferenceBase reference = AndroidDomUtil.getAndroidResourceReference(styleAttribute, true);

      if (reference != null) {
        return new ViewStyleUsageData(tag, styleAttribute, reference);
      }
    }
    else if (domElement instanceof Style) {
      final AndroidResourceReferenceBase reference = AndroidDomUtil.getAndroidResourceReference(((Style)domElement).getParentStyle(), true);

      if (reference != null) {
        return new ParentStyleUsageData((Style)domElement, reference);
      }
    }
    return null;
  }

  @Nullable
  static LayoutUsageData getLayoutUsageData(@NotNull XmlTag tag) {
    final Project project = tag.getProject();
    final DomElement domElement = DomManager.getDomManager(project).getDomElement(tag);

    if (domElement instanceof Include) {
      final GenericAttributeValue<ResourceValue> layoutAttribute = ((Include)domElement).getLayout();
      final AndroidResourceReferenceBase reference = AndroidDomUtil.getAndroidResourceReference(layoutAttribute, true);

      if (reference != null) {
        return new LayoutUsageData(project, tag, reference);
      }
    }
    return null;
  }

  static void doInlineStyleDeclaration(@NotNull Project project,
                                       @NotNull MyStyleData data,
                                       @Nullable final StyleUsageData usageData,
                                       @NotNull ErrorReporter errorReporter,
                                       @Nullable AndroidInlineTestConfig testConfig) {
    final Style style = data.myStyleElement;
    final Map<AndroidAttributeInfo, String> attributeValues = AndroidRefactoringUtil.computeAttributeMap(style, errorReporter,
                                                                                                         AndroidBundle.message(
                                                                                                           "android.inline.style.title"));
    if (attributeValues == null) {
      return;
    }
    final StyleRefData parentStyleRef = AndroidRefactoringUtil.getParentStyle(style);
    boolean inlineThisOnly;

    if (testConfig != null) {
      inlineThisOnly = testConfig.isInlineThisOnly();
    }
    else {
      final boolean invokedOnReference = usageData != null;
      final AndroidInlineStyleDialog dialog = new AndroidInlineStyleDialog(
        project, data.myReferredElement, style.getXmlTag(), data.myStyleName,
        attributeValues, parentStyleRef, invokedOnReference, invokedOnReference);
      dialog.show();

      if (!dialog.isOK()) {
        return;
      }
      inlineThisOnly = dialog.isInlineThisOnly();
    }

    if (inlineThisOnly) {
      assert usageData != null;
      final PsiFile file = usageData.getFile();

      if (file == null) {
        return;
      }
      new WriteCommandAction(project, AndroidBundle.message("android.inline.style.command.name", data.myStyleName), file) {
        @Override
        protected void run(final Result result) throws Throwable {
          usageData.inline(attributeValues, parentStyleRef);
        }

        @Override
        protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
          return UndoConfirmationPolicy.REQUEST_CONFIRMATION;
        }
      }.execute();
    }
    else if (testConfig != null) {
      final AndroidInlineAllStyleUsagesProcessor processor = new AndroidInlineAllStyleUsagesProcessor(
        project, data.myReferredElement, style.getXmlTag(), data.myStyleName,
        attributeValues, parentStyleRef, testConfig);
      processor.setPreviewUsages(false);
      processor.run();
    }
  }

  @Nullable
  static MyStyleData getInlinableStyleDataFromContext(@Nullable PsiElement context) {
    if (context instanceof LazyValueResourceElementWrapper) {
      context = ((LazyValueResourceElementWrapper)context).computeElement();
    }
    if (context == null || !context.getManager().isInProject(context)) {
      return null;
    }
    final XmlAttributeValue attrValue = PsiTreeUtil.getParentOfType(context, XmlAttributeValue.class, false);
    final XmlTag tag = attrValue != null ? PsiTreeUtil.getParentOfType(attrValue, XmlTag.class) : null;

    if (tag == null) {
      return null;
    }
    final MyStyleData data = getInlinableStyleData(tag);
    return data != null && PsiEquivalenceUtil.areElementsEquivalent(data.myReferredElement, attrValue)
           ? data : null;
  }

  static void addReferences(@NotNull PsiElement element, @NotNull Collection<UsageInfo> result) {
    for (PsiReference reference : ReferencesSearch.search(element)) {
      result.add(new UsageInfo(reference.getElement()));
    }
  }

  @NotNull
  static MultiMap<PsiElement, String> buildConflicts(Collection<PsiElement> nonXmlUsages,
                                                     Collection<PsiElement> unambiguousUsages,
                                                     Collection<PsiElement> unsupportedUsages,
                                                     Collection<PsiElement> implicitlyInherited) {
    final MultiMap<PsiElement, String> result = new MultiMap<PsiElement, String>();

    for (PsiElement usage : nonXmlUsages) {
      result.putValue(usage, "Non-XML reference '" + toString(usage) + "' won't be updated");
    }

    for (PsiElement usage : unambiguousUsages) {
      result.putValue(usage, "Unambiguous reference '" + toString(usage) + "' won't be updated");
    }

    for (PsiElement usage : unsupportedUsages) {
      result.putValue(usage, "Unsupported reference '" + toString(usage) + "' won't be updated");
    }

    for (PsiElement usage : implicitlyInherited) {
      result.putValue(usage, "The style has implicit inheritor '" + toString(usage) + "' which won't be updated");
    }
    return result;
  }

  private static String toString(PsiElement element) {
    return element instanceof XmlAttributeValue
           ? ((XmlAttributeValue)element).getValue()
           : element.getText();
  }

  static void doInlineLayoutFile(@NotNull Project project,
                                 @NotNull XmlFile layoutFile,
                                 @Nullable PsiElement usageElement,
                                 @Nullable AndroidInlineTestConfig testConfig) {
    final XmlTag rootTag = layoutFile.getRootTag();
    assert rootTag != null;

    if (testConfig == null) {
      final AndroidInlineLayoutDialog dialog = new AndroidInlineLayoutDialog(project, layoutFile, rootTag, usageElement);
      dialog.show();
    }
    else {
      final AndroidInlineLayoutProcessor processor =
        new AndroidInlineLayoutProcessor(project, layoutFile, rootTag, testConfig.isInlineThisOnly() ? usageElement : null, testConfig);
      processor.setPreviewUsages(false);
      processor.run();
    }
  }

  static class MyStyleData {
    private final String myStyleName;
    private final Style myStyleElement;
    private final PsiElement myReferredElement;

    MyStyleData(String styleName, Style styleElement, PsiElement referredElement) {
      myStyleName = styleName;
      myStyleElement = styleElement;
      myReferredElement = referredElement;
    }
  }
}
