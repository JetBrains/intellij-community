// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import static org.junit.Assert.assertNotNull;

public final class StructureViewComponentTestUtil {
  private StructureViewComponentTestUtil() {
  }

  /**
   * Initializes the structure view for the file that the fixture shows in the editor, and passes the view to the consumer.
   * The view is built and checked on the EDT.
   *
   * @param consumer the callback in which the actual testing of the structure view is performed.
   */
  public static void testStructureView(@NotNull CodeInsightTestFixture fixture,
                                       @NotNull Consumer<? super StructureViewComponent> consumer) {
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiFile file = fixture.getFile();
      assertNotNull("configure first", file);
      VirtualFile virtualFile = file.getVirtualFile();

      FileEditor fileEditor = FileEditorManager.getInstance(fixture.getProject()).getSelectedEditor(virtualFile);
      assertNotNull("editor not opened for " + virtualFile, virtualFile);

      StructureViewBuilder builder = LanguageStructureViewBuilder.getInstance().getStructureViewBuilder(file);
      assertNotNull("no builder for " + virtualFile, builder);

      StructureViewComponent component = null;
      try {
        component = (StructureViewComponent)builder.createStructureView(fileEditor, fixture.getProject());
        PlatformTestUtil.waitForPromise(component.rebuildAndUpdate());
        consumer.consume(component);
      }
      finally {
        if (component != null) Disposer.dispose(component);
      }
    });
  }
}
