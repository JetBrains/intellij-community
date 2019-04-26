// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EditorTestUtil;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("SameParameterValue")
public class EditorConfigTrailingSpacesTest extends CodeInsightTestCase {

  private EditorSettingsExternalizable.OptionSet oldSettings;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    oldSettings = settings.getState();
    settings.loadState(new EditorSettingsExternalizable.OptionSet());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      EditorSettingsExternalizable.getInstance().loadState(oldSettings);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testKeepTrailingSpacesOnSave() throws Exception {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    settings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE);
    PsiFile ecFile = createFile(
      ".editorconfig",

      "root = true\n" +
      "[*]\n" +
      "trim_trailing_whitespace=false");

    final String originalText =
      "class Foo {             \n" +
      "  void foo()        \n" +
      "}";
    PsiFile source = createFile(getModule(), ecFile.getVirtualFile().getParent(), "source.java", originalText);
    configureByExistingFile(source.getVirtualFile());
    type(' ');
    EditorTestUtil.executeAction(getEditor(),"SaveAll");
    assertEquals(originalText, getEditor().getDocument().getText().trim());
  }


  public void testRemoveTrailingSpacesOnSave() throws Exception {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    settings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE);
    PsiFile ecFile = createFile(
      ".editorconfig",

      "root = true\n" +
      "[*]\n" +
      "trim_trailing_whitespace=true");

    final String originalText =
      "class Foo {             \n" +
      "  void foo()        \n" +
      "}";
    PsiFile source = createFile(getModule(), ecFile.getVirtualFile().getParent(), "source.java", originalText);
    configureByExistingFile(source.getVirtualFile());
    type(' ');
    EditorTestUtil.executeAction(getEditor(),"SaveAll");
    assertEquals(
      "class Foo {\n" +
      "  void foo()\n" +
      "}",

      getEditor().getDocument().getText().trim());
  }

  public void testEnsureNewLine() throws Exception {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    settings.setEnsureNewLineAtEOF(false);
    PsiFile ecFile = createFile(
      ".editorconfig",

      "root = true\n" +
      "[*]\n" +
      "insert_final_newline=true");

    final String originalText =
      "class Foo {\n" +
      "  void foo()\n" +
      "}";
    PsiFile source = createFile(getModule(), ecFile.getVirtualFile().getParent(), "source.java", originalText);
    configureByExistingFile(source.getVirtualFile());
    type(' ');
    EditorTestUtil.executeAction(getEditor(),"SaveAll");
    assertEquals(
      " class Foo {\n" +
      "  void foo()\n" +
      "}\n",

      getEditor().getDocument().getText());
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("editorconfig") + "/testData/org/editorconfig/configmanagement/fileSettings/";
  }

}
