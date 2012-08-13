package org.jetbrains.android.refactoring;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.android.dom.converters.AndroidResourceReference;
import org.jetbrains.android.dom.layout.LayoutViewElement;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.dom.resources.Style;
import org.jetbrains.android.dom.resources.StyleItem;
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.ErrorReporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  static StyleUsageData getUsageData(@NotNull XmlTag tag) {
    final DomElement domElement = DomManager.getDomManager(tag.getProject()).getDomElement(tag);

    if (domElement instanceof LayoutViewElement) {
      final GenericAttributeValue<ResourceValue> styleAttribute = ((LayoutViewElement)domElement).getStyle();
      final AndroidResourceReference reference = getAndroidResourceReference(styleAttribute);

      if (reference != null) {
        return new ViewStyleUsageData(tag, styleAttribute, reference);
      }
    }
    else if (domElement instanceof Style) {
      final AndroidResourceReference reference = getAndroidResourceReference(((Style)domElement).getParentStyle());

      if (reference != null) {
        return new ParentStyleUsageData((Style)domElement, reference);
      }
    }
    return null;
  }

  @Nullable
  private static AndroidResourceReference getAndroidResourceReference(@Nullable GenericAttributeValue<ResourceValue> attribute) {
    if (attribute == null) {
      return null;
    }

    final ResourceValue styleValue = attribute.getValue();
    if (styleValue == null || styleValue.getPackage() != null) {
      return null;
    }

    final XmlAttributeValue styleAttributeValue = attribute.getXmlAttributeValue();
    if (styleAttributeValue == null) {
      return null;
    }

    for (PsiReference reference : styleAttributeValue.getReferences()) {
      if (reference instanceof AndroidResourceReference) {
        return (AndroidResourceReference)reference;
      }
    }
    return null;
  }

  @Nullable
  static Map<AndroidAttributeInfo, String> computeAttributeMap(@NotNull Style style, @NotNull ErrorReporter errorReporter) {
    final Map<AndroidAttributeInfo, String> attributeValues = new HashMap<AndroidAttributeInfo, String>();

    for (StyleItem item : style.getItems()) {
      final String attributeName = item.getName().getStringValue();
      String attributeValue = item.getStringValue();

      if (attributeName == null || attributeName.length() <= 0 || attributeValue == null) {
        continue;
      }
      final int idx = attributeName.indexOf(':');
      final String localName = idx >= 0 ? attributeName.substring(idx + 1) : attributeName;
      final String nsPrefix = idx >= 0 ? attributeName.substring(0, idx) : null;

      if (nsPrefix != null) {
        if (!AndroidUtils.SYSTEM_RESOURCE_PACKAGE.equals(nsPrefix)) {
          errorReporter.report(RefactoringBundle.getCannotRefactorMessage("Unknown XML attribute prefix '" + nsPrefix + ":'"),
                               AndroidBundle.message("android.inline.style.title"));
          return null;
        }
      }
      else {
        errorReporter.report(
          RefactoringBundle.getCannotRefactorMessage("The style contains attribute without 'android' prefix."),
          AndroidBundle.message("android.inline.style.title"));
        return null;
      }
      attributeValues.put(new AndroidAttributeInfo(localName, nsPrefix), attributeValue);
    }
    return attributeValues;
  }

  static void doInlineStyleDeclaration(@NotNull Project project,
                                       @NotNull MyStyleData data,
                                       @Nullable final StyleUsageData usageData,
                                       @NotNull ErrorReporter errorReporter,
                                       @Nullable AndroidInlineTestConfig testConfig) {
    final Style style = data.myStyleElement;
    final Map<AndroidAttributeInfo, String> attributeValues = computeAttributeMap(style, errorReporter);
    if (attributeValues == null) {
      return;
    }
    final StyleRefData parentStyleRef = getParentStyle(style);
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
        project, data.myReferredElement, style.getXmlTag(), data.myStyleName, attributeValues, parentStyleRef);
      processor.setPreviewUsages(false);
      processor.run();
    }
  }

  @Nullable
  static StyleRefData getParentStyle(@NotNull Style style) {
    final ResourceValue parentStyleRefValue = style.getParentStyle().getValue();

    if (parentStyleRefValue != null) {
      final String parentStyleName = parentStyleRefValue.getResourceName();

      if (parentStyleName != null) {
        return new StyleRefData(parentStyleName, parentStyleRefValue.getPackage());
      }
    }
    else {
      final String styleName = style.getName().getStringValue();

      if (styleName != null) {
        final int idx = styleName.lastIndexOf('.');

        if (idx > 0) {
          return new StyleRefData(styleName.substring(0, idx), null);
        }
      }
    }
    return null;
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
