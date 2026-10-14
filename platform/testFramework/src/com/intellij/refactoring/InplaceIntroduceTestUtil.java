// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * In-place introduce checks for a JUnit 5 test.
 * This is the JUnit 5 replacement of {@link AbstractInplaceIntroduceTest}.
 */
@TestOnly
public final class InplaceIntroduceTestUtil {
  private InplaceIntroduceTestUtil() {
  }

  /**
   * Configures {@code <basePath><name><extension>} from the test data path of {@code fixture}.
   * Then starts the introducer with {@code invokeRefactoring}, gives it to {@code pass}, and finishes the template with {@code gotoEnd(false)}.
   * The template state comes from the top-level editor, so the caret can be in an injected fragment.
   * Then checks the result against {@code <basePath><name>_after<extension>}.
   * The test method name gives {@code name}. For example, {@code testReplaceAll} gives {@code replaceAll}.
   * {@code invokeRefactoring} and {@code pass} run on the EDT in a command.
   * Call it from any thread.
   */
  @SuppressWarnings("rawtypes")
  public static void doTest(@NotNull CodeInsightTestFixture fixture,
                            @NotNull String basePath,
                            @NotNull String extension,
                            @NotNull String testMethodName,
                            @NotNull Supplier<? extends AbstractInplaceIntroducer> invokeRefactoring,
                            @Nullable Consumer<? super AbstractInplaceIntroducer> pass) {
    doTest(fixture, basePath, extension, testMethodName, invokeRefactoring, pass, false);
  }

  /**
   * Does the same as {@link #doTest}, but finishes the template with {@code gotoEnd(true)}, as the escape key does.
   * The template state comes from the editor of {@code fixture}.
   * Call it from any thread.
   */
  @SuppressWarnings("rawtypes")
  public static void doTestEscape(@NotNull CodeInsightTestFixture fixture,
                                  @NotNull String basePath,
                                  @NotNull String extension,
                                  @NotNull String testMethodName,
                                  @NotNull Supplier<? extends AbstractInplaceIntroducer> invokeRefactoring,
                                  @Nullable Consumer<? super AbstractInplaceIntroducer> pass) {
    doTest(fixture, basePath, extension, testMethodName, invokeRefactoring, pass, true);
  }

  @SuppressWarnings("rawtypes")
  private static void doTest(@NotNull CodeInsightTestFixture fixture,
                             @NotNull String basePath,
                             @NotNull String extension,
                             @NotNull String testMethodName,
                             @NotNull Supplier<? extends AbstractInplaceIntroducer> invokeRefactoring,
                             @Nullable Consumer<? super AbstractInplaceIntroducer> pass,
                             boolean escape) {
    String name = PlatformTestUtil.getTestName(testMethodName, true);
    fixture.configureByFile(basePath + name + extension);
    withInplaceRename(fixture, () -> {
      runInCommand(fixture.getProject(), () -> {
        AbstractInplaceIntroducer introducer = invokeRefactoring.get();
        if (pass != null) {
          pass.accept(introducer);
        }
        Editor editor = escape ? fixture.getEditor() : InjectedLanguageEditorUtil.getTopLevelEditor(fixture.getEditor());
        TemplateState state = Objects.requireNonNull(TemplateManagerImpl.getTemplateState(editor));
        state.gotoEnd(escape);
        UIUtil.dispatchAllInvocationEvents();
      });
      fixture.checkResultByFile(basePath + name + "_after" + extension);
    });
  }

  /**
   * Enables the template testing mode and the in-place rename in the editor of {@code fixture}, and runs {@code body}.
   * Then restores the in-place rename setting.
   * Configure the editor before the call. {@code body} runs on the calling thread.
   * Call it from any thread.
   */
  public static <E extends Throwable> void withInplaceRename(@NotNull CodeInsightTestFixture fixture, @NotNull ThrowableRunnable<E> body)
    throws E {
    Editor editor = fixture.getEditor();
    boolean enabled = EdtTestUtil.runInEdtAndGet(() -> {
      boolean old = editor.getSettings().isVariableInplaceRenameEnabled();
      TemplateManagerImpl.setTemplateTesting(fixture.getTestRootDisposable());
      editor.getSettings().setVariableInplaceRenameEnabled(true);
      return old;
    });
    try {
      body.run();
    }
    finally {
      EdtTestUtil.runInEdtAndWait(() -> editor.getSettings().setVariableInplaceRenameEnabled(enabled));
    }
  }

  /**
   * Runs {@code body} on the EDT in a command, as a JUnit 3 light test ran its whole body.
   * Rethrows an exception of {@code body}.
   * Call it from any thread.
   */
  public static void runInCommand(@NotNull Project project, @NotNull Runnable body) {
    EdtTestUtil.runInEdtAndWait(() -> {
      Ref<Throwable> error = new Ref<>();
      CommandProcessor.getInstance().executeCommand(project, () -> {
        try {
          body.run();
        }
        catch (Throwable e) {
          error.set(e);
        }
      }, null, null);
      if (!error.isNull()) {
        ExceptionUtil.rethrow(error.get());
      }
    });
  }
}
