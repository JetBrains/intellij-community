package org.jetbrains.android.refactoring;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.OrderedSet;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.XmlName;
import org.jetbrains.android.dom.converters.AndroidResourceReference;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
class AndroidInlineAllStyleUsagesProcessor extends BaseRefactoringProcessor {
  private final PsiElement myStyleElement;
  private final String myStyleName;
  private final Map<XmlName, String> myAttributeValues;
  private final AndroidInlineUtil.MyStyleRefData myParentStyleRef;
  private final XmlTag myStyleTag;

  protected AndroidInlineAllStyleUsagesProcessor(@NotNull Project project,
                                                 @NotNull PsiElement styleElement,
                                                 @NotNull XmlTag styleTag,
                                                 @NotNull String styleName,
                                                 @NotNull Map<XmlName, String> attributeValues,
                                                 @Nullable AndroidInlineUtil.MyStyleRefData parentStyleRef) {
    super(project);
    myStyleElement = styleElement;
    myStyleTag = styleTag;
    myStyleName = styleName;
    myAttributeValues = attributeValues;
    myParentStyleRef = parentStyleRef;
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new UsageViewDescriptorAdapter() {
      @NotNull
      @Override
      public PsiElement[] getElements() {
        return new PsiElement[]{myStyleElement};
      }

      @Override
      public String getCodeReferencesText(int usagesCount, int filesCount) {
        return "References to be inlined" + UsageViewBundle.getReferencesString(usagesCount, filesCount);
      }

      @Override
      public String getProcessedElementsHeader() {
        return "Style to inline";
      }
    };
  }

  @NotNull
  @Override
  protected UsageInfo[] findUsages() {
    final Set<UsageInfo> usages = new HashSet<UsageInfo>();
    addReferences(myStyleElement, usages);

    for (PsiField field : AndroidResourceUtil.findResourceFieldsForValueResource(myStyleTag, false)) {
      addReferences(field, usages);
    }
    return usages.toArray(new UsageInfo[usages.size()]);
  }

  private static void addReferences(@NotNull PsiElement element, @NotNull Collection<UsageInfo> result) {
    for (PsiReference reference : ReferencesSearch.search(element)) {
      result.add(new UsageInfo(reference.getElement()));
    }
  }


  @Override
  protected void performRefactoring(UsageInfo[] usages) {
    final List<MyUsageInlineInfo> inlineInfos = new ArrayList<MyUsageInlineInfo>();
    final List<PsiElement> nonXmlUsages = new ArrayList<PsiElement>();
    final List<PsiElement> unsupportedUsages = new ArrayList<PsiElement>();
    final List<PsiElement> unambiguousUsages = new ArrayList<PsiElement>();

    for (UsageInfo usage : usages) {
      final PsiElement element = usage.getElement();
      if (element == null) continue;

      if (element.getLanguage() != XMLLanguage.INSTANCE) {
        nonXmlUsages.add(element);
        continue;
      }
      final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
      final Pair<AndroidResourceReference, GenericAttributeValue<ResourceValue>> pair =
        tag != null ? AndroidInlineUtil.findStyleReference(tag) : null;

      if (pair == null) {
        unsupportedUsages.add(element);
        continue;
      }

      if (pair.getFirst().computeTargetElements().length > 1) {
        unambiguousUsages.add(element);
        continue;
      }
      inlineInfos.add(new MyUsageInlineInfo(tag, pair.getSecond()));
    }

    if (nonXmlUsages.size() > 0 || unambiguousUsages.size() > 0 || unsupportedUsages.size() > 0) {
      final String errorMessage = buildErrorMessage(myProject, nonXmlUsages, unambiguousUsages, unsupportedUsages);
      AndroidUtils.reportError(myProject, errorMessage, AndroidBundle.message("android.inline.style.title"));
      return;
    }

    for (MyUsageInlineInfo info : inlineInfos) {
      AndroidInlineUtil.inlineStyleUsage(info.getTag(), info.getStyleAttribute(), myAttributeValues, myParentStyleRef);
    }
    myStyleTag.delete();
  }

  @Override
  protected String getCommandName() {
    return AndroidBundle.message("android.inline.style.command.name", myStyleName);
  }

  @Override
  protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
    // do it because the refactoring can be invoked from UI designer
    return UndoConfirmationPolicy.REQUEST_CONFIRMATION;
  }

  @NotNull
  private static String buildErrorMessage(Project project,
                                          Collection<PsiElement> nonXmlUsages,
                                          Collection<PsiElement> unambiguousUsages,
                                          Collection<PsiElement> unsupportedUsages) {
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

  private static class MyUsageInlineInfo {
    private final XmlTag myTag;
    private final GenericAttributeValue<ResourceValue> myStyleAttribute;

    private MyUsageInlineInfo(@NotNull XmlTag tag, @NotNull GenericAttributeValue<ResourceValue> styleAttribute) {
      myTag = tag;
      myStyleAttribute = styleAttribute;
    }

    @NotNull
    public XmlTag getTag() {
      return myTag;
    }

    @NotNull
    public GenericAttributeValue<ResourceValue> getStyleAttribute() {
      return myStyleAttribute;
    }
  }
}
