package com.intellij.testAssistant;

import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiMethod;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 5/24/11 4:59 PM
 */
public class TestDataRelatedItem extends GotoRelatedItem{
  
  private final List<String> myTestDataFiles = new ArrayList<String>();
  private final Editor myEditor;
  private final PsiMethod myMethod;

  public TestDataRelatedItem(@NotNull PsiMethod method, @NotNull Editor editor, @NotNull Collection<String> testDataFiles) {
    super(method);
    myMethod = method;
    myEditor = editor;
    myTestDataFiles.addAll(testDataFiles);
  }

  @Override
  public String getCustomName() {
    if (myTestDataFiles.size() != 1) {
      return "test data";
    }
    return PathUtil.getFileName(myTestDataFiles.get(0));
  }

  @Override
  public void navigate() {
    TestDataNavigationHandler.navigate(myMethod, JBPopupFactory.getInstance().guessBestPopupLocation(myEditor), myTestDataFiles);
  }
}
