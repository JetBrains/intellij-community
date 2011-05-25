package com.intellij.testAssistant;

import com.intellij.navigation.GotoRelatedItem;
import com.intellij.navigation.GotoRelatedProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 5/24/11 4:52 PM
 */
public class TestDataAsRelatedFileProvider extends GotoRelatedProvider {

  @NotNull
  @Override
  public List<? extends GotoRelatedItem> getItems(@NotNull DataContext context) {
    final Editor editor = PlatformDataKeys.EDITOR.getData(context);
    final Project project = PlatformDataKeys.PROJECT.getData(context);
    final PsiElement element = LangDataKeys.PSI_ELEMENT.getData(context);
    if (editor == null || element == null || project == null) {
      return Collections.emptyList(); 
    }

    PsiMethod method = null;
    for (PsiElement e = element; e != null; e = e.getParent()) {
      if (e instanceof PsiMethod) {
        method = (PsiMethod)e;
        break;
      }
    }
    if (method == null) {
      return Collections.emptyList();
    } 
    
    final List<String> testDataFiles = NavigateToTestDataAction.findTestDataFiles(context);
    if (testDataFiles == null || testDataFiles.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.singletonList(new TestDataRelatedItem(method, editor, testDataFiles));
  }
}
