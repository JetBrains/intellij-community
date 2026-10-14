// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider;
import com.intellij.openapi.fileEditor.impl.HTMLFileEditorMock;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.CoroutineKt.executeSomeCoroutineTasksAndDispatchAllInvocationEvents;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.fileEditorManagerFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


@TestApplication
public final class HTMLEditorProviderTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);
  private final TestFixture<FileEditorManagerImpl> managerFixture = fileEditorManagerFixture(projectFixture, false);

  @TestDisposable
  private Disposable testRootDisposable;

  /**
   * Regression test for IJPL-194278 in the real JCEF-backed HTML editor.
   *
   * <p>This test is skipped when JCEF is not available. That commonly happens when the test is started
   * from IDE run configuration, so {@link #testReopenHtmlEditorKeepsHtmlProviderWithoutJcef()} exists
   * as a companion scenario that validates reopen logic without JCEF.
   */
  @Test
  public void testReopenHtmlEditorKeepsHtmlProvider() {
    Project project = projectFixture.get();
    FileEditorManagerImpl manager = managerFixture.get();
    Registry.get("ide.browser.jcef.headless.enabled").setValue(true, testRootDisposable);
    Registry.get("ide.browser.jcef.testMode.enabled").setValue(true, testRootDisposable);
    if (!JBCefApp.isSupported()) {
      return;
    }

    HTMLEditorProvider.Request request = HTMLEditorProvider.Request.Companion.html("<html><body>test</body></html>");
    FileEditor editor = EdtTestUtil.runInEdtAndGet(() -> HTMLEditorProvider.openEditor(project, "html-preview", request));
    assertNotNull(editor);

    VirtualFile file = editor.getFile();
    FileEditor[] reopenedEditors = EdtTestUtil.runInEdtAndGet(() -> {
      manager.closeFile(file);
      executeSomeCoroutineTasksAndDispatchAllInvocationEvents(project);
      return manager.openFile(file, true);
    });
    assertNotNull(ContainerUtil.find(reopenedEditors, it -> it instanceof HTMLEditorProvider.HTMLFileEditor));
  }

  /**
   * Companion regression test for environments without JCEF.
   *
   * <p>This exists primarily because running this test class directly from IDE run configuration often
   * has no JCEF support, while we still want reopen behavior to be validated with {@code openEditorWithoutJcef}.
   */
  @Test
  public void testReopenHtmlEditorKeepsHtmlProviderWithoutJcef() {
    Project project = projectFixture.get();
    FileEditorManagerImpl manager = managerFixture.get();
    Registry.get("ide.browser.jcef.enabled").setValue(false, testRootDisposable);
    Registry.get("ide.browser.jcef.testMode.enabled").setValue(true, testRootDisposable);

    HTMLEditorProvider.Request request = HTMLEditorProvider.Request.Companion.html("<html><body>test</body></html>");
    FileEditor editor = EdtTestUtil.runInEdtAndGet(
      () -> HTMLEditorProvider.openEditorWithoutJcef(project, "html-preview", request, PlainTextFileType.INSTANCE));
    assertNotNull(editor);
    assertEquals(HTMLFileEditorMock.UNIT_TEST_EDITOR_NAME, editor.getName());

    VirtualFile file = editor.getFile();
    FileEditor[] reopenedEditors = EdtTestUtil.runInEdtAndGet(() -> {
      manager.closeFile(file);
      executeSomeCoroutineTasksAndDispatchAllInvocationEvents(project);
      return manager.openFile(file, true);
    });
    FileEditor reopened = ContainerUtil.find(reopenedEditors, it -> it instanceof HTMLEditorProvider.HTMLFileEditor);
    assertNotNull(reopened);
    assertEquals(HTMLFileEditorMock.UNIT_TEST_EDITOR_NAME, reopened.getName());
  }

  @Test
  public void testReopenClosedTabActionUsesCurrentWindowClosedTabsWithoutContextComponent() {
    Project project = projectFixture.get();
    FileEditorManagerImpl manager = managerFixture.get();
    Registry.get("ide.browser.jcef.enabled").setValue(false, testRootDisposable);
    Registry.get("ide.browser.jcef.testMode.enabled").setValue(true, testRootDisposable);

    HTMLEditorProvider.Request request = HTMLEditorProvider.Request.Companion.html("<html><body>test</body></html>");
    FileEditor htmlEditor = EdtTestUtil.runInEdtAndGet(
      () -> HTMLEditorProvider.openEditorWithoutJcef(project, "html-preview", request, PlainTextFileType.INSTANCE));
    assertNotNull(htmlEditor);
    assertEquals(HTMLFileEditorMock.UNIT_TEST_EDITOR_NAME, htmlEditor.getName());

    VirtualFile htmlFile = htmlEditor.getFile();
    VirtualFile regularFile = codeInsightFixture.get().addFileToProject("foo.txt", "foo").getVirtualFile();
    EdtTestUtil.runInEdtAndWait(() -> {
      manager.openFile(regularFile, true);
      manager.closeFile(htmlFile);
      executeSomeCoroutineTasksAndDispatchAllInvocationEvents(project);
    });

    assertFalse(manager.isFileOpen(htmlFile));
    assertTrue(manager.isFileOpen(regularFile));

    EdtTestUtil.runInEdtAndWait(() -> {
      ActionManager.getInstance().getAction("ReopenClosedTab").actionPerformed(
        TestActionEvent.createTestEvent(SimpleDataContext.getProjectContext(project))
      );
      executeSomeCoroutineTasksAndDispatchAllInvocationEvents(project);
    });

    assertTrue(manager.isFileOpen(htmlFile));
    FileEditor reopened = ContainerUtil.find(manager.getEditors(htmlFile), it -> it instanceof HTMLEditorProvider.HTMLFileEditor);
    assertNotNull(reopened);
    assertEquals(HTMLFileEditorMock.UNIT_TEST_EDITOR_NAME, reopened.getName());
  }
}
