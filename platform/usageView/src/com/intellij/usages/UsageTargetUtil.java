package com.intellij.usages;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UsageTargetUtil {
  private static final ExtensionPointName<UsageTargetProvider> EP_NAME = ExtensionPointName.create("com.intellij.usageTargetProvider");
  public void foo() {}

  public static UsageTarget[] findUsageTargets(DataProvider dataProvider) {
    Editor editor = (Editor)dataProvider.getData(DataConstants.EDITOR);
    PsiFile file = (PsiFile)dataProvider.getData(DataConstants.PSI_FILE);

    List<UsageTarget> result = new ArrayList<UsageTarget>();
    if (file != null && editor != null) {
      UsageTarget[] targets = findUsageTargets(editor, file);
      if (targets != null ) Collections.addAll(result, targets);
    }
    PsiElement psiElement = (PsiElement)dataProvider.getData(DataConstants.PSI_ELEMENT);
    if (psiElement != null) {
      UsageTarget[] targets = findUsageTargets(psiElement);
      if (targets != null )Collections.addAll(result, targets);
    }

    return result.isEmpty() ? null : result.toArray(new UsageTarget[result.size()]);
  }

  public static UsageTarget[] findUsageTargets(Editor editor, PsiFile file) {
    List<UsageTarget> result = new ArrayList<UsageTarget>();
    for (UsageTargetProvider provider : Extensions.getExtensions(EP_NAME)) {
      UsageTarget[] targets = provider.getTargets(editor, file);
      if (targets != null) Collections.addAll(result, targets);
    }
    return result.isEmpty() ? null : result.toArray(new UsageTarget[result.size()]);
  }

  public static UsageTarget[] findUsageTargets(PsiElement psiElement) {
    List<UsageTarget> result = new ArrayList<UsageTarget>();
    for (UsageTargetProvider provider : Extensions.getExtensions(EP_NAME)) {
      UsageTarget[] targets = provider.getTargets(psiElement);
      if (targets != null) Collections.addAll(result, targets);
    }
    return result.isEmpty() ? null : result.toArray(new UsageTarget[result.size()]);
  }
}
