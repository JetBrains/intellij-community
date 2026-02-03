// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.find.findUsages;

import com.intellij.find.FindBundle;
import com.intellij.find.FindManager;
import com.intellij.ide.presentation.VirtualFilePresentation;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.navigation.PsiElementNavigationItem;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.UiCompatibleDataProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiPresentableMetaData;
import com.intellij.refactoring.RefactoringUiService;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.ConfigurableUsageTarget;
import com.intellij.usages.PsiElementUsageTarget;
import com.intellij.usages.UsageView;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class PsiElement2UsageTargetAdapter
  implements PsiElementUsageTarget, UiCompatibleDataProvider, PsiElementNavigationItem, ItemPresentation, ConfigurableUsageTarget {
  private final SmartPsiElementPointer<?> myPointer;
  protected final @NotNull FindUsagesOptions myOptions;
  private String myPresentableText;
  private String myLocationText;
  private Icon myIcon;

  public PsiElement2UsageTargetAdapter(@NotNull PsiElement element, @NotNull FindUsagesOptions options, boolean update) {
    if (!(element instanceof NavigationItem)) {
      throw new IllegalArgumentException("Element is not a navigation item: " + element);
    }
    myOptions = options;
    PsiFile file = element.getContainingFile();
    myPointer = file == null ? SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element) :
                SmartPointerManager.getInstance(file.getProject()).createSmartPsiElementPointer(element, file);
    if (update) {
      update(element, file);
    }
  }

  public PsiElement2UsageTargetAdapter(@NotNull PsiElement element, boolean update) {
    this(element, new FindUsagesOptions(element.getProject()), update);
  }

  /**
   * Consider to use {@link #PsiElement2UsageTargetAdapter(PsiElement, boolean)} to avoid
   * calling {@link #update()} that could lead to freeze. {@link #update()} should be called on bg thread.
   */
  @Deprecated(forRemoval = true)
  public PsiElement2UsageTargetAdapter(@NotNull PsiElement element) {
    this(element, true);
  }

  @Override
  public String getName() {
    PsiElement element = getElement();
    return element instanceof NavigationItem ? ((NavigationItem)element).getName() : null;
  }

  @Override
  public @NotNull ItemPresentation getPresentation() {
    return this;
  }

  @Override
  public void navigate(boolean requestFocus) {
    PsiElement element = getElement();
    if (element instanceof Navigatable && ((Navigatable)element).canNavigate()) {
      ((Navigatable)element).navigate(requestFocus);
    }
  }

  @Override
  public boolean canNavigate() {
    PsiElement element = getElement();
    return element instanceof Navigatable && ((Navigatable)element).canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    PsiElement element = getElement();
    return element instanceof Navigatable && ((Navigatable)element).canNavigateToSource();
  }

  @Override
  public PsiElement getTargetElement() {
    return getElement();
  }

  @Override
  public String toString() {
    return getPresentableText();
  }

  @Override
  public void findUsages() {
    PsiElement element = getElement();
    if (element != null) {
      RefactoringUiService.getInstance().startFindUsages(element, myOptions);
    }
  }

  @Override
  public PsiElement getElement() {
    return myPointer.getElement();
  }

  @Override
  public void findUsagesInEditor(@NotNull FileEditor editor) {
    PsiElement element = getElement();
    FindManager.getInstance(element.getProject()).findUsagesInEditor(element, editor);
  }

  @Override
  public void highlightUsages(@NotNull PsiFile file, @NotNull Editor editor, boolean clearHighlights) {
    PsiElement target = getElement();

    RefactoringUiService.getInstance().highlightUsageReferences(file, target, editor, clearHighlights);
  }

  @Override
  public boolean isValid() {
    return getElement() != null;
  }

  @Override
  public boolean isReadOnly() {
    return isValid() && !getElement().isWritable();
  }

  @Override
  public VirtualFile[] getFiles() {
    if (!isValid()) return null;

    final PsiFile psiFile = getElement().getContainingFile();
    if (psiFile == null) return null;

    final VirtualFile virtualFile = psiFile.getVirtualFile();
    return virtualFile == null ? null : new VirtualFile[]{virtualFile};
  }

  /**
   * @deprecated use {@link #convert(PsiElement[], boolean)} instead
   */
  @Deprecated(forRemoval = true)
  public static PsiElement2UsageTargetAdapter @NotNull [] convert(PsiElement @NotNull [] psiElements) {
    return convert(psiElements, true);
  }

  public static PsiElement2UsageTargetAdapter @NotNull [] convert(PsiElement @NotNull [] psiElements, boolean update) {
    PsiElement2UsageTargetAdapter[] targets = new PsiElement2UsageTargetAdapter[psiElements.length];
    for (int i = 0; i < targets.length; i++) {
      targets[i] = new PsiElement2UsageTargetAdapter(psiElements[i], update);
    }

    return targets;
  }

  public static PsiElement @NotNull [] convertToPsiElements(PsiElement2UsageTargetAdapter @NotNull [] adapters) {
    PsiElement[] targets = new PsiElement[adapters.length];
    for (int i = 0; i < targets.length; i++) {
      targets[i] = adapters[i].getElement();
    }

    return targets;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(UsageView.USAGE_SCOPE, myOptions.searchScope);
    sink.lazy(UsageView.USAGE_INFO_KEY, () -> {
      PsiElement element = getElement();
      return element != null && element.getTextRange() != null ? new UsageInfo(element) : null;
    });
  }

  @Override
  public KeyboardShortcut getShortcut() {
    return UsageViewUtil.getShowUsagesWithSettingsShortcut();
  }

  @Override
  public @Nls @NotNull String getLongDescriptiveName() {
    PsiElement psiElement = getElement();

    return psiElement == null ? UsageViewBundle.message("node.invalid") :
           FindBundle.message("recent.find.usages.action.popup", StringUtil.capitalize(UsageViewUtil.getType(psiElement)),
                              DescriptiveNameUtil.getDescriptiveName(psiElement),
                              myOptions.searchScope.getDisplayName()
           );
  }

  @Override
  public void showSettings() {
    PsiElement element = getElement();
    if (element != null) {
      RefactoringUiService.getInstance().findUsages(myPointer.getProject(), element, null, null, true, null);
    }
  }

  @Override
  public void update() {
    PsiElement element = getElement();
    if (element != null) {
      update(element, element.getContainingFile());
    }
  }

  private void update(@NotNull PsiElement element, PsiFile file) {
    if (file == null ? element.isValid() : file.isValid()) {
      final ItemPresentation presentation = ((NavigationItem)element).getPresentation();
      myIcon = presentation == null ? null : presentation.getIcon(true);
      myPresentableText = presentation == null ? UsageViewUtil.createNodeText(element) : presentation.getPresentableText();
      myLocationText = presentation == null ? null : StringUtil.nullize(presentation.getLocationString());
      if (myIcon == null) {
        if (element instanceof PsiMetaOwner psiMetaOwner) {
          final PsiMetaData metaData = psiMetaOwner.getMetaData();
          if (metaData instanceof PsiPresentableMetaData psiPresentableMetaData) {
            if (myIcon == null) myIcon = psiPresentableMetaData.getIcon();
          }
        }
        else if (element instanceof PsiFile psiFile) {
          final VirtualFile virtualFile = psiFile.getVirtualFile();
          if (virtualFile != null) {
            myIcon = VirtualFilePresentation.getIcon(virtualFile);
          }
        }
      }
    }
  }

  @Override
  public String getPresentableText() {
    return myPresentableText;
  }

  @Override
  public @Nullable String getLocationString() {
    return myLocationText;
  }

  @Override
  public Icon getIcon(boolean open) {
    return myIcon;
  }

  public @NotNull Project getProject() {
    return myPointer.getProject();
  }
}