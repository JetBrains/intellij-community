package org.jetbrains.android.refactoring;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.dom.resources.ResourceNameConverter;
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
  private final Map<AndroidAttributeInfo, String> myAttributeValues;
  private final StyleRefData myParentStyleRef;
  private final XmlTag myStyleTag;

  protected AndroidInlineAllStyleUsagesProcessor(@NotNull Project project,
                                                 @NotNull PsiElement styleElement,
                                                 @NotNull XmlTag styleTag,
                                                 @NotNull String styleName,
                                                 @NotNull Map<AndroidAttributeInfo, String> attributeValues,
                                                 @Nullable StyleRefData parentStyleRef) {
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
    AndroidInlineUtil.addReferences(myStyleElement, usages);

    for (PsiField field : AndroidResourceUtil.findResourceFieldsForValueResource(myStyleTag, false)) {
      AndroidInlineUtil.addReferences(field, usages);
    }
    return usages.toArray(new UsageInfo[usages.size()]);
  }


  @Override
  protected void performRefactoring(UsageInfo[] usages) {
    final List<StyleUsageData> inlineInfos = new ArrayList<StyleUsageData>();
    final List<PsiElement> nonXmlUsages = new ArrayList<PsiElement>();
    final List<PsiElement> unsupportedUsages = new ArrayList<PsiElement>();
    final List<PsiElement> unambiguousUsages = new ArrayList<PsiElement>();
    final List<PsiElement> implicitlyInherited = new ArrayList<PsiElement>();

    for (UsageInfo usage : usages) {
      final PsiElement element = usage.getElement();
      if (element == null) continue;

      if (element.getLanguage() != XMLLanguage.INSTANCE) {
        nonXmlUsages.add(element);
        continue;
      }
      final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
      StyleUsageData usageData = tag != null ? AndroidInlineUtil.getStyleUsageData(tag) : null;

      if (usageData == null) {
        if (usage.getReference() instanceof ResourceNameConverter.MyParentStyleReference) {
          implicitlyInherited.add(element);
        }
        else {
          unsupportedUsages.add(element);
        }
        continue;
      }

      if (usageData.getReference().computeTargetElements().length > 1) {
        unambiguousUsages.add(element);
        continue;
      }
      inlineInfos.add(usageData);
    }

    if (nonXmlUsages.size() > 0 ||
        unambiguousUsages.size() > 0 ||
        unsupportedUsages.size() > 0 ||
        implicitlyInherited.size() > 0) {
      final String errorMessage = AndroidInlineUtil
        .buildErrorMessage(myProject, nonXmlUsages, unambiguousUsages, unsupportedUsages, implicitlyInherited);
      AndroidUtils.reportError(myProject, errorMessage, AndroidBundle.message("android.inline.style.title"));
      return;
    }

    for (StyleUsageData info : inlineInfos) {
      info.inline(myAttributeValues, myParentStyleRef);
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
}
