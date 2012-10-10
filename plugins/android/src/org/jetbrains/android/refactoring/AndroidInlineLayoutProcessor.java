package org.jetbrains.android.refactoring;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidInlineLayoutProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.refactoring.AndroidInlineLayoutProcessor");

  private final XmlFile myLayoutFile;
  private final XmlTag myLayoutRootTag;
  private final PsiElement myUsageElement;
  private final AndroidInlineTestConfig myTestConfig;

  protected AndroidInlineLayoutProcessor(@NotNull Project project,
                                         @NotNull XmlFile file,
                                         @NotNull XmlTag rootTag,
                                         @Nullable PsiElement usageElement,
                                         @Nullable AndroidInlineTestConfig config) {
    super(project);
    myLayoutFile = file;
    myLayoutRootTag = rootTag;
    myUsageElement = usageElement;
    myTestConfig = config;
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new UsageViewDescriptorAdapter() {
      @NotNull
      @Override
      public PsiElement[] getElements() {
        return new PsiElement[]{myLayoutFile};
      }

      @Override
      public String getCodeReferencesText(int usagesCount, int filesCount) {
        return "References to be inlined" + UsageViewBundle.getReferencesString(usagesCount, filesCount);
      }

      @Override
      public String getProcessedElementsHeader() {
        return "Layout file to inline";
      }
    };
  }

  @NotNull
  @Override
  protected UsageInfo[] findUsages() {
    if (myUsageElement != null) {
      return new UsageInfo[] {new UsageInfo(myUsageElement)};
    }
    final Set<UsageInfo> usages = new HashSet<UsageInfo>();
    AndroidInlineUtil.addReferences(myLayoutFile, usages);

    for (PsiField field : AndroidResourceUtil.findResourceFieldsForFileResource(myLayoutFile, false)) {
      AndroidInlineUtil.addReferences(field, usages);
    }
    return usages.toArray(new UsageInfo[usages.size()]);
  }

  @Override
  protected void performRefactoring(UsageInfo[] usages) {
    final List<LayoutUsageData> inlineInfos = new ArrayList<LayoutUsageData>();

    for (UsageInfo usage : usages) {
      final PsiElement element = usage.getElement();
      if (element == null) continue;

      final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
      final LayoutUsageData usageData = tag != null
                                        ? AndroidInlineUtil.getLayoutUsageData(tag)
                                        : null;
      if (usageData != null && usageData.getReference().computeTargetElements().length == 1) {
        inlineInfos.add(usageData);
      }
    }

    for (LayoutUsageData info : inlineInfos) {
      try {
        info.inline(myLayoutRootTag);
      }
      catch (AndroidRefactoringErrorException e) {
        LOG.info(e);
        String message = e.getMessage();

        if (message == null) {
          message = "Refactoring was performed with errors";
        }
        AndroidUtils.reportError(myProject, message, AndroidBundle.message("android.inline.style.title"));
        return;
      }
    }
    if (myUsageElement == null) {
      try {
        myLayoutFile.delete();
      }
      catch (IncorrectOperationException e) {
        // see IDEA-90562 and http://code.google.com/p/android/issues/detail?id=36435
        final Throwable c = e.getCause();

        if (c instanceof IOException && c.getMessage() != null) {
          AndroidUtils.reportError(myProject, c.getMessage(), AndroidBundle.message("android.inline.style.title"));
          return;
        }
        throw e;
      }
    }
  }

  @Override
  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    final UsageInfo[] usages = refUsages.get();
    final MultiMap<PsiElement, String> conflicts = detectConflicts(usages);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myTestConfig.setConflicts(conflicts);
      return true;
    }
    return showConflicts(conflicts, usages);
  }

  private static MultiMap<PsiElement, String> detectConflicts(UsageInfo[] usages) {
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
      final LayoutUsageData usageData = tag != null
                                        ? AndroidInlineUtil.getLayoutUsageData(tag)
                                        : null;
      if (usageData == null) {
        unsupportedUsages.add(element);
        continue;
      }

      if (usageData.getReference().computeTargetElements().length > 1) {
        unambiguousUsages.add(element);
      }
    }
    return AndroidInlineUtil.buildConflicts(nonXmlUsages, unambiguousUsages, unsupportedUsages,
                                            Collections.<PsiElement>emptyList());
  }

  @Override
  protected String getCommandName() {
    return AndroidBundle.message("android.inline.layout.command.name", myLayoutFile.getName());
  }

  @Override
  protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
    // do it because the refactoring can be invoked from UI designer
    return UndoConfirmationPolicy.REQUEST_CONFIRMATION;
  }
}
