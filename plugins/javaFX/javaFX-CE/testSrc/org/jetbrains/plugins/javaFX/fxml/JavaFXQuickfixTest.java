/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JavaFXQuickfixTest extends DaemonAnalyzerTestCase {
  @Override
  protected void setUpModule() {
    super.setUpModule();
    PsiTestUtil.addLibrary(getModule(), "javafx", PluginPathManager.getPluginHomePath("javaFX") + "/testData", "jfxrt.jar");
  }

  public void testCreateControllerMethod() throws Exception {
    doTest("Create Method 'void bar(ActionEvent)'", true);
  }

  public void testCreateField() throws Exception {
    doTest("Create Field 'btn'", true);
  }

  private void doTest(final String actionName) throws Exception {
    doTest(actionName, false);
  }

  private void doTest(final String actionName, boolean changeEditor) throws Exception {
    configureByFiles(null, getTestName(true) + ".fxml", getTestName(false) + ".java");
    final List<HighlightInfo> infos = doHighlighting();
    findAndInvokeIntentionAction(infos, actionName, getEditor(), getFile());
    if (changeEditor) {
      final PsiElement targetMethod = TargetElementUtilBase.findTargetElement(getEditor(), TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED);
      assertNotNull(targetMethod);
      final VirtualFile file = targetMethod.getContainingFile().getVirtualFile();
      assertNotNull(file);
      final Editor editor = FileEditorManager.getInstance(getProject()).openTextEditor(new OpenFileDescriptor(getProject(), file), true);
      assertNotNull(editor);
      setActiveEditor(editor);
    }
    checkResultByFile(getTestName(false) + "_after.java");
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/quickfix/";
  }
}
