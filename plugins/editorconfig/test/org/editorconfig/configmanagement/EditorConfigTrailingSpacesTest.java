// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.AppTopics;
import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@SuppressWarnings("SameParameterValue")
public class EditorConfigTrailingSpacesTest extends CodeInsightTestCase {

  private EditorSettingsExternalizable.OptionSet oldSettings;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    oldSettings = settings.getState();
    settings.loadState(new EditorSettingsExternalizable.OptionSet());
    DocumentSettingsManager documentSettingsManager = new DocumentSettingsManager(getProject());
    MessageBus bus = getProject().getMessageBus();
    MessageBusConnection connection = bus.connect();
    connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, documentSettingsManager);
    disposeOnTearDown(connection);
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
    PsiFile ecFile = createFile(".editorconfig", "[*]");
    final String originalText =
      "class Foo {             \n" +
      "  void foo()        \n" +
      "}";
    PsiFile source = createFile(getModule(), ecFile.getVirtualFile().getParent(), "source.java", originalText);
    configureByExistingFile(source.getVirtualFile());
    type(' ');
    EditorTestUtil.executeAction(getEditor(),"SaveAll");
    //assertEquals(originalText, getEditor().getDocument().getText().trim());
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("editorconfig") + "/testData/org/editorconfig/configmanagement/fileSettings/";
  }

}
