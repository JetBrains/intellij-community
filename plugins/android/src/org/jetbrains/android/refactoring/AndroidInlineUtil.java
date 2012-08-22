package org.jetbrains.android.refactoring;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.OrderedSet;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.android.dom.converters.AndroidResourceReferenceBase;
import org.jetbrains.android.dom.layout.Include;
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

import java.util.Collection;
import java.util.Iterator;
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
      final AndroidResourceReferenceBase reference = getAndroidResourceReference(styleAttribute);

      if (reference != null) {
        return new ViewStyleUsageData(tag, styleAttribute, reference);
      }
    }
    else if (domElement instanceof Style) {
      final AndroidResourceReferenceBase reference = getAndroidResourceReference(((Style)domElement).getParentStyle());

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
      final AndroidResourceReferenceBase reference = getAndroidResourceReference(layoutAttribute);

      if (reference != null) {
        return new LayoutUsageData(project, tag, reference);
      }
    }
    return null;
  }

  @Nullable
  private static AndroidResourceReferenceBase getAndroidResourceReference(@Nullable GenericAttributeValue<ResourceValue> attribute) {
    if (attribute == null) {
      return null;
    }

    final ResourceValue resValue = attribute.getValue();
    if (resValue == null || resValue.getPackage() != null) {
      return null;
    }

    final XmlAttributeValue value = attribute.getXmlAttributeValue();
    if (value == null) {
      return null;
    }

    for (PsiReference reference : value.getReferences()) {
      if (reference instanceof AndroidResourceReferenceBase) {
        return (AndroidResourceReferenceBase)reference;
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

  static void addReferences(@NotNull PsiElement element, @NotNull Collection<UsageInfo> result) {
    for (PsiReference reference : ReferencesSearch.search(element)) {
      result.add(new UsageInfo(reference.getElement()));
    }
  }

  @NotNull
  static String buildErrorMessage(Project project,
                                          Collection<PsiElement> nonXmlUsages,
                                          Collection<PsiElement> unambiguousUsages,
                                          Collection<PsiElement> unsupportedUsages,
                                          Collection<PsiElement> implicitlyInherited) {
    final StringBuilder builder = new StringBuilder("Cannot perform refactoring\n\n");

    if (nonXmlUsages.size() > 0) {
      builder.append("Non-XML references are not supported:\n");
      buildString(builder, project, nonXmlUsages);
      builder.append("\n\n");
    }

    if (unambiguousUsages.size() > 0) {
      builder.append("Unambiguous references:\n");
      buildString(builder, project, unambiguousUsages);
      builder.append("\n\n");
    }

    if (unsupportedUsages.size() > 0) {
      builder.append("Unsupported references:\n");
      buildString(builder, project, unsupportedUsages);
      builder.append("\n\n");
    }

    if (implicitlyInherited.size() > 0) {
      builder.append("Implicit inheritance is not supported:\n");
      buildString(builder, project, implicitlyInherited);
      builder.append("\n\n");
    }
    builder.delete(builder.length() - 2, builder.length());
    return builder.toString();
  }

  private static void buildString(StringBuilder builder, Project project, Collection<PsiElement> invalidRefs) {
    final OrderedSet<String> lines = new OrderedSet<String>();

    for (PsiElement usage : invalidRefs) {
      final PsiFile psiFile = usage.getContainingFile();
      final VirtualFile file = psiFile != null
                               ? psiFile.getVirtualFile()
                               : null;
      if (file != null) {
        lines.add("    in '" + getPresentableFilePath(project, file) + "'");
      }
      else {
        lines.add("    in unknown file");
      }
    }

    for (Iterator<String> it = lines.iterator(); it.hasNext(); ) {
      final String line = it.next();
      builder.append(line);

      if (it.hasNext()) {
        builder.append('\n');
      }
    }
  }

  private static String getPresentableFilePath(Project project, VirtualFile file) {
    final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(project);
    final int contentRootCount = projectRootManager.getContentRoots().length;

    if (contentRootCount == 0) {
      return file.getPath();
    }
    final VirtualFile contentRoot = projectRootManager.getFileIndex().getContentRootForFile(file);

    if (contentRoot == null) {
      return file.getPath();
    }
    final String relativePath = VfsUtilCore.getRelativePath(file, contentRoot, '/');

    if (relativePath == null) {
      return file.getPath();
    }
    final String presentableRelativePath = contentRootCount == 1
                                           ? relativePath
                                           : contentRoot.getName() + '/' + relativePath;
    return FileUtil.toSystemDependentName(".../" + presentableRelativePath);
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
        new AndroidInlineLayoutProcessor(project, layoutFile, rootTag, testConfig.isInlineThisOnly() ? usageElement : null);
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
