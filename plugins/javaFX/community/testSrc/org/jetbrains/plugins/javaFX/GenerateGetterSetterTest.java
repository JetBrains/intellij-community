// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX;

import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture;
import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.GenerateGetterAndSetterHandler;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.DaemonAnalyzerFixture.daemonAnalyzerFixture;

@TestApplication
public class GenerateGetterSetterTest {
  private final TestFixture<DaemonAnalyzerFixture> daemonFixture = daemonAnalyzerFixture(getTestDataPath());

  @Test
  public void testDouble() {
    doTest();
  }

  @Test
  public void testStringNumber() {
    doTest();
  }

  @BeforeEach
  void setUp() {
    JavaFxFixture.addJavaFxJarAsLibrary(daemonFixture.get().getModule());
  }

  private void doTest() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFile("/generateGetterSetter/before" + fixture.getTestName(false) + ".java");
    fixture.runInCommand(() -> {
      new GenerateGetterAndSetterHandler() {
        @Override
        protected ClassMember @Nullable [] chooseMembers(ClassMember[] members,
                                                         boolean allowEmptySelection,
                                                         boolean copyJavadocCheckbox,
                                                         Project project,
                                                         @Nullable Editor editor) {
          return members;
        }
      }.invoke(fixture.getProject(), fixture.getEditor(), fixture.getFile());
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    });
    fixture.checkResultByFile("/generateGetterSetter/after" + fixture.getTestName(false) + ".java");
  }
  
  private static @NotNull String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData";
  }
}
