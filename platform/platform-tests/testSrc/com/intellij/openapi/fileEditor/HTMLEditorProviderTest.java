// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider;
import com.intellij.openapi.fileEditor.impl.HTMLFileEditorMock;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.util.containers.ContainerUtil;

import static com.intellij.testFramework.CoroutineKt.executeSomeCoroutineTasksAndDispatchAllInvocationEvents;


public final class HTMLEditorProviderTest extends FileEditorManagerTestCase {

  /**
   * Regression test for IJPL-194278 in the real JCEF-backed HTML editor.
   *
   * <p>This test is skipped when JCEF is not available. That commonly happens when the test is started
   * from IDE run configuration, so {@link #testReopenHtmlEditorKeepsHtmlProviderWithoutJcef()} exists
   * as a companion scenario that validates reopen logic without JCEF.
   */
  public void testReopenHtmlEditorKeepsHtmlProvider() {
    Registry.get("ide.browser.jcef.headless.enabled").setValue(true, getTestRootDisposable());
    Registry.get("ide.browser.jcef.testMode.enabled").setValue(true, getTestRootDisposable());
    if (!JBCefApp.isSupported()) {
      return;
    }

    HTMLEditorProvider.Request request = HTMLEditorProvider.Request.Companion.html("<html><body>test</body></html>");
    FileEditor editor = HTMLEditorProvider.openEditor(getProject(), "html-preview", request);
    assertNotNull(editor);

    VirtualFile file = editor.getFile();
    manager.closeFile(file);
    executeSomeCoroutineTasksAndDispatchAllInvocationEvents(getProject());

    FileEditor[] reopenedEditors = manager.openFile(file, true);
    assertNotNull(ContainerUtil.find(reopenedEditors, it -> it instanceof HTMLEditorProvider.HTMLFileEditor));
  }

  /**
   * Companion regression test for environments without JCEF.
   *
   * <p>This exists primarily because running this test class directly from IDE run configuration often
   * has no JCEF support, while we still want reopen behavior to be validated with {@code openEditorWithoutJcef}.
   */
  public void testReopenHtmlEditorKeepsHtmlProviderWithoutJcef() {
    Registry.get("ide.browser.jcef.enabled").setValue(false, getTestRootDisposable());
    Registry.get("ide.browser.jcef.testMode.enabled").setValue(true, getTestRootDisposable());

    HTMLEditorProvider.Request request = HTMLEditorProvider.Request.Companion.html("<html><body>test</body></html>");
    FileEditor editor = HTMLEditorProvider.openEditorWithoutJcef(getProject(), "html-preview", request, PlainTextFileType.INSTANCE);
    assertNotNull(editor);
    assertEquals(HTMLFileEditorMock.UNIT_TEST_EDITOR_NAME, editor.getName());

    VirtualFile file = editor.getFile();
    manager.closeFile(file);
    executeSomeCoroutineTasksAndDispatchAllInvocationEvents(getProject());

    FileEditor[] reopenedEditors = manager.openFile(file, true);
    FileEditor reopened = ContainerUtil.find(reopenedEditors, it -> it instanceof HTMLEditorProvider.HTMLFileEditor);
    assertNotNull(reopened);
    assertEquals(HTMLFileEditorMock.UNIT_TEST_EDITOR_NAME, reopened.getName());
  }

  public void testReopenClosedTabActionUsesCurrentWindowClosedTabsWithoutContextComponent() {
    Registry.get("ide.browser.jcef.enabled").setValue(false, getTestRootDisposable());
    Registry.get("ide.browser.jcef.testMode.enabled").setValue(true, getTestRootDisposable());

    HTMLEditorProvider.Request request = HTMLEditorProvider.Request.Companion.html("<html><body>test</body></html>");
    FileEditor htmlEditor = HTMLEditorProvider.openEditorWithoutJcef(getProject(), "html-preview", request, PlainTextFileType.INSTANCE);
    assertNotNull(htmlEditor);
    assertEquals(HTMLFileEditorMock.UNIT_TEST_EDITOR_NAME, htmlEditor.getName());

    VirtualFile htmlFile = htmlEditor.getFile();
    VirtualFile regularFile = myFixture.addFileToProject("foo.txt", "foo").getVirtualFile();
    manager.openFile(regularFile, true);
    manager.closeFile(htmlFile);
    executeSomeCoroutineTasksAndDispatchAllInvocationEvents(getProject());

    assertFalse(manager.isFileOpen(htmlFile));
    assertTrue(manager.isFileOpen(regularFile));

    ActionManager.getInstance().getAction("ReopenClosedTab").actionPerformed(
      TestActionEvent.createTestEvent(SimpleDataContext.getProjectContext(getProject()))
    );
    executeSomeCoroutineTasksAndDispatchAllInvocationEvents(getProject());

    assertTrue(manager.isFileOpen(htmlFile));
    FileEditor reopened = ContainerUtil.find(manager.getEditors(htmlFile), it -> it instanceof HTMLEditorProvider.HTMLFileEditor);
    assertNotNull(reopened);
    assertEquals(HTMLFileEditorMock.UNIT_TEST_EDITOR_NAME, reopened.getName());
  }
}
