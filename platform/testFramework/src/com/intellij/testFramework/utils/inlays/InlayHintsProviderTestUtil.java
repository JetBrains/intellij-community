// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.inlays;

import com.intellij.codeInsight.hints.CollectorWithSettings;
import com.intellij.codeInsight.hints.InlayDumpUtil;
import com.intellij.codeInsight.hints.InlayHintsCollector;
import com.intellij.codeInsight.hints.InlayHintsProvider;
import com.intellij.codeInsight.hints.InlayHintsSinkImpl;
import com.intellij.codeInsight.hints.LinearOrderInlayRenderer;
import com.intellij.codeInsight.hints.presentation.PresentationRenderer;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Inlay hints provider checks for a JUnit 5 test.
 * This is the replacement of {@link InlayHintsProviderTestCase}.
 */
@TestOnly
public final class InlayHintsProviderTestUtil {
  private static final String NO_HINTS_PREFIX = "// NO_HINTS";

  private InlayHintsProviderTestUtil() {
  }

  /**
   * Checks that {@code expectedText} has no inlays when it has a line that starts with {@code // NO_HINTS},
   * and that it has inlays otherwise.
   * Call it from any thread.
   */
  public static void verifyHintsPresence(@NotNull String expectedText) {
    boolean expectedNoHints = expectedText.lines().anyMatch(line -> line.startsWith(NO_HINTS_PREFIX));
    boolean actualNoHints = !InlayDumpUtil.INSTANCE.getInlayPattern().matcher(expectedText).find();
    assertEquals(expectedNoHints, actualNoHints, "Hint presence should match the use of the " + NO_HINTS_PREFIX + " directive.");
  }

  /**
   * Runs {@link #doTestProvider(CodeInsightTestFixture, String, String, InlayHintsProvider, Object, boolean)}
   * with the default settings of {@code provider} and without the hint presence check.
   */
  public static <T> void doTestProvider(@NotNull CodeInsightTestFixture fixture,
                                        @NotNull String fileName,
                                        @NotNull String expectedText,
                                        @NotNull InlayHintsProvider<T> provider) {
    doTestProvider(fixture, fileName, expectedText, provider, provider.createSettings(), false);
  }

  /**
   * Runs {@link #doTestProvider(CodeInsightTestFixture, String, String, InlayHintsProvider, Object, boolean)}
   * without the hint presence check.
   */
  public static <T> void doTestProvider(@NotNull CodeInsightTestFixture fixture,
                                        @NotNull String fileName,
                                        @NotNull String expectedText,
                                        @NotNull InlayHintsProvider<T> provider,
                                        @NotNull T settings) {
    doTestProvider(fixture, fileName, expectedText, provider, settings, false);
  }

  /**
   * Removes the inlays from {@code expectedText} and configures the result as {@code fileName} in {@code fixture}.
   * Then collects the hints of {@code provider} with {@code settings} and compares the dump with {@code expectedText}.
   * When {@code verifyHintPresence} is {@code true}, also runs {@link #verifyHintsPresence(String)}.
   * Call it from any thread. The hint collection runs on the EDT.
   */
  public static <T> void doTestProvider(@NotNull CodeInsightTestFixture fixture,
                                        @NotNull String fileName,
                                        @NotNull String expectedText,
                                        @NotNull InlayHintsProvider<T> provider,
                                        @NotNull T settings,
                                        boolean verifyHintPresence) {
    String sourceText = InlayDumpUtil.INSTANCE.removeInlays(expectedText);
    fixture.configureByText(fileName, sourceText);
    String actualText = dumpInlayHints(fixture, sourceText, provider, settings);
    assertEquals(expectedText, actualText);

    if (verifyHintPresence) {
      verifyHintsPresence(expectedText);
    }
  }

  /**
   * Runs {@link #dumpInlayHints(CodeInsightTestFixture, String, InlayHintsProvider, Object, boolean)}
   * with the default settings of {@code provider}.
   */
  public static <T> @NotNull String dumpInlayHints(@NotNull CodeInsightTestFixture fixture,
                                                   @NotNull String sourceText,
                                                   @NotNull InlayHintsProvider<T> provider) {
    return dumpInlayHints(fixture, sourceText, provider, provider.createSettings(), false);
  }

  /**
   * Runs {@link #dumpInlayHints(CodeInsightTestFixture, String, InlayHintsProvider, Object, boolean)}
   * with the block inlays above the line.
   */
  public static <T> @NotNull String dumpInlayHints(@NotNull CodeInsightTestFixture fixture,
                                                   @NotNull String sourceText,
                                                   @NotNull InlayHintsProvider<T> provider,
                                                   @NotNull T settings) {
    return dumpInlayHints(fixture, sourceText, provider, settings, false);
  }

  /**
   * Collects the hints of {@code provider} with {@code settings} in the file and the editor of {@code fixture}.
   * Then returns {@code sourceText} with the inlays of the editor in it.
   * When {@code renderBelowLineBlockInlaysBelowTheLine} is {@code true}, the block inlays below the line go after the line.
   * Call it from any thread. The hint collection runs on the EDT.
   */
  public static <T> @NotNull String dumpInlayHints(@NotNull CodeInsightTestFixture fixture,
                                                   @NotNull String sourceText,
                                                   @NotNull InlayHintsProvider<T> provider,
                                                   @NotNull T settings,
                                                   boolean renderBelowLineBlockInlaysBelowTheLine) {
    return EdtTestUtil.runInEdtAndGet(() -> {
      PsiFile file = fixture.getFile();
      Editor editor = fixture.getEditor();
      InlayHintsSinkImpl sink = new InlayHintsSinkImpl(editor);
      InlayHintsCollector collector = provider.getCollectorFor(file, editor, settings, sink);
      if (collector == null) {
        throw new IllegalStateException("Collector is expected");
      }
      CollectorWithSettings<T> collectorWithSettings = new CollectorWithSettings<>(collector, provider.getKey(), file.getLanguage(), sink);
      collectorWithSettings.collectTraversingAndApply(editor, file, true);
      return InlayDumpUtil.INSTANCE.dumpInlays(sourceText, editor, null, (renderer, ignored) -> {
        if (!(renderer instanceof PresentationRenderer) && !(renderer instanceof LinearOrderInlayRenderer<?>)) {
          throw new IllegalStateException("renderer not supported");
        }
        return renderer.toString();
      }, 0, renderBelowLineBlockInlaysBelowTheLine, false);
    });
  }
}
