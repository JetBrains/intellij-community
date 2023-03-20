// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.usageView;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.ElementDescriptionUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageView;
import com.intellij.usages.impl.UsageViewStatisticsCollector;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public final class UsageViewUtil {
  private static final Logger LOG = Logger.getInstance(UsageViewUtil.class);

  private UsageViewUtil() { }

  @NotNull
  public static @NlsSafe String createNodeText(@NotNull PsiElement element) {
    return ElementDescriptionUtil.getElementDescription(element, UsageViewNodeTextLocation.INSTANCE);
  }

  @NotNull
  public static @NlsSafe String getShortName(@NotNull PsiElement psiElement) {
    PsiUtilCore.ensureValid(psiElement);
    return ElementDescriptionUtil.getElementDescription(psiElement, UsageViewShortNameLocation.INSTANCE);
  }

  @NotNull
  public static @NlsSafe String getLongName(@NotNull PsiElement psiElement) {
    PsiUtilCore.ensureValid(psiElement);
    return ElementDescriptionUtil.getElementDescription(psiElement, UsageViewLongNameLocation.INSTANCE);
  }

  @NotNull
  public static @NlsSafe String getType(@NotNull PsiElement psiElement) {
    return ElementDescriptionUtil.getElementDescription(psiElement, UsageViewTypeLocation.INSTANCE);
  }

  private static boolean hasNonCodeUsages(UsageInfo @NotNull [] usages) {
    for (UsageInfo usage : usages) {
      if (usage.isNonCodeUsage) return true;
    }
    return false;
  }

  private static boolean hasUsagesInGeneratedCode(UsageInfo @NotNull [] usages, @NotNull Project project) {
    for (UsageInfo usage : usages) {
      VirtualFile file = usage.getVirtualFile();
      if (file != null && GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(file, project)) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasReadOnlyUsages(UsageInfo @NotNull [] usages) {
    for (UsageInfo usage : usages) {
      if (!usage.isWritable()) return true;
    }
    return false;
  }

  public static UsageInfo @NotNull [] removeDuplicatedUsages(UsageInfo @NotNull [] usages) {
    Set<UsageInfo> set = new LinkedHashSet<>(Arrays.asList(usages));

    // Replace duplicates of move rename usage infos in injections from non code usages of master files
    String newTextInNonCodeUsage =
      Arrays.stream(usages)
            .filter(usage -> usage instanceof NonCodeUsageInfo)
            .map(usage -> ((NonCodeUsageInfo)usage).newText)
            .findFirst().orElse(null);

    if (newTextInNonCodeUsage != null) {
      for(UsageInfo usage:usages) {
        if (!(usage instanceof MoveRenameUsageInfo)) continue;
        PsiFile file = usage.getFile();

        if (file != null) {
          PsiElement context = InjectedLanguageManager.getInstance(file.getProject()).getInjectionHost(file);
          if (context != null) {

            PsiElement usageElement = usage.getElement();
            if (usageElement == null) continue;

            PsiReference psiReference = usage.getReference();
            if (psiReference == null) continue;

            int injectionOffsetInMasterFile = InjectedLanguageManager.getInstance(usageElement.getProject()).injectedToHost(usageElement, usageElement.getTextOffset());
            TextRange rangeInElement = usage.getRangeInElement();
            assert rangeInElement != null : usage;
            TextRange range = rangeInElement.shiftRight(injectionOffsetInMasterFile);
            PsiFile containingFile = context.getContainingFile();
            if (containingFile == null) continue; //move package to another package
            set.remove(
              NonCodeUsageInfo.create(
                containingFile,
                range.getStartOffset(),
                range.getEndOffset(),
                ((MoveRenameUsageInfo)usage).getReferencedElement(),
                newTextInNonCodeUsage
              )
            );
          }
        }
      }
    }
    return set.toArray(UsageInfo.EMPTY_ARRAY);
  }

  public static UsageInfo @NotNull [] toUsageInfoArray(@NotNull final Collection<? extends UsageInfo> collection) {
    final int size = collection.size();
    return size == 0 ? UsageInfo.EMPTY_ARRAY : collection.toArray(new UsageInfo[size]);
  }

  public static PsiElement @NotNull [] toElements(UsageInfo @NotNull [] usageInfos) {
    return ContainerUtil.map2Array(usageInfos, PsiElement.class, UsageInfo::getElement);
  }

  public static void navigateTo(@NotNull UsageInfo info, boolean requestFocus) {
    int offset = info.getNavigationOffset();
    VirtualFile file = info.getVirtualFile();
    Project project = info.getProject();
    if (file != null) {
      UsageViewStatisticsCollector.logUsageNavigate(project, info);
      FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, file, offset), requestFocus);
    }
  }

  @NotNull
  public static Set<UsageInfo> getNotExcludedUsageInfos(@NotNull UsageView usageView) {
    Set<Usage> excludedUsages = usageView.getExcludedUsages();

    Set<UsageInfo> usageInfos = new LinkedHashSet<>();
    for (Usage usage : usageView.getUsages()) {
      if (usage instanceof UsageInfo2UsageAdapter && !excludedUsages.contains(usage)) {
        UsageInfo usageInfo = ((UsageInfo2UsageAdapter)usage).getUsageInfo();
        usageInfos.add(usageInfo);
      }
    }
    return usageInfos;
  }

  public static boolean reportNonRegularUsages(UsageInfo @NotNull [] usages, @NotNull Project project) {
    boolean inGeneratedCode = hasUsagesInGeneratedCode(usages, project);
    if (hasNonCodeUsages(usages) || inGeneratedCode) {
      StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
      if (statusBar != null) {
        statusBar.setInfo(UsageViewBundle.message(inGeneratedCode
                                                    ? "occurrences.found.in.comments.strings.non.java.files.and.generated.code"
                                                    : "occurrences.found.in.comments.strings.and.non.java.files"));
      }
      return true;
    }
    return false;
  }

  @Nullable
  public static KeyboardShortcut getShowUsagesWithSettingsShortcut() {
    return ActionManager.getInstance().getKeyboardShortcut("ShowSettingsAndFindUsages");
  }
}