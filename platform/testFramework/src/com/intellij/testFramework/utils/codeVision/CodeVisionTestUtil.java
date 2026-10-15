// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.codeVision;

import com.intellij.codeInsight.codeVision.CodeVisionHost;
import com.intellij.codeInsight.codeVision.CodeVisionProvider;
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings;
import com.intellij.codeInsight.codeVision.ui.model.CodeVisionListData;
import com.intellij.codeInsight.codeVision.ui.renderers.CodeVisionInlayRenderer;
import com.intellij.codeInsight.hints.InlayDumpUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Code vision checks for a JUnit 5 test.
 * This is the JUnit 5 replacement of {@link CodeVisionTestCase}.
 */
@TestOnly
public final class CodeVisionTestUtil {
  /**
   * The author hint of {@code VcsCodeVisionProvider} in a preview.
   */
  public static final String AUTHOR_HINT = "John Smith +2";

  private CodeVisionTestUtil() {
  }

  /**
   * Enables the new code vision and the code vision test mode until the test root disposable of {@code fixture} is disposed.
   * At the dispose, enables all code vision providers again on the EDT.
   * Call it in {@code @BeforeEach}, from any thread.
   */
  public static void setUp(@NotNull CodeInsightTestFixture fixture) {
    Disposable disposable = fixture.getTestRootDisposable();
    Project project = fixture.getProject();
    Registry.get("editor.codeVision.new").setValue(true, disposable);
    TestModeFlags.set(CodeVisionHost.Companion.isCodeVisionTestKey(), true, disposable);
    Disposer.register(disposable, () -> setProvidersEnabled(project, ignored -> true));
  }

  /**
   * Enables only the code vision providers of {@code enabledProviderGroupIds} in the project of {@code fixture}.
   * Call it from any thread. The settings change runs on the EDT, because it drops the PSI caches.
   */
  public static void enableOnlyProviders(@NotNull CodeInsightTestFixture fixture, String @NotNull ... enabledProviderGroupIds) {
    List<String> enabledIds = List.of(enabledProviderGroupIds);
    setProvidersEnabled(fixture.getProject(), enabledIds::contains);
  }

  /**
   * Runs {@link #testProviders(CodeInsightTestFixture, String, Consumer, boolean, String...)}.
   * It configures {@code fileName} with {@link CodeInsightTestFixture#configureByText}, and allows other inlays in the editor.
   */
  public static void testProviders(@NotNull CodeInsightTestFixture fixture,
                                   @NotNull String expectedText,
                                   @NotNull String fileName,
                                   String @NotNull ... enabledProviderGroupIds) {
    testProviders(fixture, expectedText, sourceText -> fixture.configureByText(fileName, sourceText), false, enabledProviderGroupIds);
  }

  /**
   * Enables only the code vision providers of {@code enabledProviderGroupIds}.
   * Then removes the inlays from {@code expectedText}, and gives the result to {@code configureFile}.
   * {@code configureFile} must open the file in the editor of {@code fixture}.
   * Then runs the highlighting, waits for the code vision, and compares the dump of the code vision hints with {@code expectedText}.
   * When {@code onlyCodeVisionHintsAllowed} is {@code true}, other inlays in the editor fail the test.
   * Call it from any thread. The code vision runs on the EDT.
   */
  public static void testProviders(@NotNull CodeInsightTestFixture fixture,
                                   @NotNull String expectedText,
                                   @NotNull Consumer<? super String> configureFile,
                                   boolean onlyCodeVisionHintsAllowed,
                                   String @NotNull ... enabledProviderGroupIds) {
    Project project = fixture.getProject();
    CodeVisionHost codeVisionHost = getCodeVisionHost(project);
    List<String> enabledIds = List.of(enabledProviderGroupIds);
    enableOnlyProviders(fixture, enabledProviderGroupIds);

    String sourceText = InlayDumpUtil.INSTANCE.removeInlays(expectedText);
    configureFile.accept(sourceText);

    Editor editor = fixture.getEditor();
    project.putUserData(CodeVisionHost.Companion.isCodeVisionTestKey(), true);
    EdtTestUtil.runInEdtAndWait(() -> {
      for (CodeVisionProvider<?> provider : codeVisionHost.getProviders()) {
        if (provider.getId().equals("vcs.code.vision") && enabledIds.contains(provider.getGroupId())) {
          provider.preparePreview(editor, fixture.getFile());
        }
      }
    });
    fixture.doHighlighting();
    String actualText = EdtTestUtil.runInEdtAndGet(() -> {
      waitForCodeVisionSync(editor, codeVisionHost, fixture.getTestRootDisposable());
      return dumpCodeVisionHints(sourceText, editor, onlyCodeVisionHintsAllowed);
    });
    assertEquals(expectedText, actualText);
  }

  /**
   * Computes the code vision of {@code editor} with {@code host}, and waits for the result.
   * Call it on the EDT.
   */
  public static void waitForCodeVisionSync(@NotNull Editor editor, @NotNull CodeVisionHost host, @NotNull Disposable disposable) {
    PlatformTestUtil.waitForFuture(host.calculateCodeVisionSync(editor, disposable));
  }

  /**
   * Returns {@code sourceText} with the code vision hints of {@code editor} in it.
   * When {@code onlyCodeVisionHintsAllowed} is {@code true}, other inlays in the editor throw an error.
   * Call it on the EDT.
   */
  public static @NotNull String dumpCodeVisionHints(@NotNull String sourceText, @NotNull Editor editor, boolean onlyCodeVisionHintsAllowed) {
    return InlayDumpUtil.INSTANCE.dumpInlays(sourceText, editor, inlay -> {
      boolean rendererSupported = inlay.getRenderer() instanceof CodeVisionInlayRenderer;
      if (onlyCodeVisionHintsAllowed && !rendererSupported) {
        throw new IllegalStateException("renderer not supported");
      }
      return rendererSupported;
    }, (ignored, inlay) -> {
      CodeVisionListData data = Objects.requireNonNull(inlay.getUserData(CodeVisionListData.KEY));
      return data.getVisibleLens().stream()
        .map(entry -> entry.getLongPresentation())
        .collect(Collectors.joining("   ", "[", "]"));
    }, 0, false, false);
  }

  private static void setProvidersEnabled(@NotNull Project project, @NotNull Predicate<? super String> enabled) {
    Set<String> groupIds = getCodeVisionHost(project).getProviders().stream().map(CodeVisionProvider::getGroupId).collect(Collectors.toSet());
    EdtTestUtil.runInEdtAndWait(() -> {
      CodeVisionSettings settings = CodeVisionSettings.getInstance();
      for (String groupId : groupIds) {
        settings.setProviderEnabled(groupId, enabled.test(groupId));
      }
    });
  }

  private static @NotNull CodeVisionHost getCodeVisionHost(@NotNull Project project) {
    return project.getService(CodeVisionHost.class);
  }
}
