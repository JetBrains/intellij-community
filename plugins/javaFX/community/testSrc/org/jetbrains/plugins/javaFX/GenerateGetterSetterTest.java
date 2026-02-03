// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.GenerateGetterAndSetterHandler;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.AbstractJavaFXTestCase;

@HeavyPlatformTestCase.WrapInCommand
public class GenerateGetterSetterTest extends DaemonAnalyzerTestCase {
  public void testDouble() throws Exception {
    doTest();
  }

  public void testStringNumber() throws Exception {
    doTest();
  }

  @Override
  protected void setUpModule() {
    super.setUpModule();
    AbstractJavaFXTestCase.addJavaFxJarAsLibrary(getModule());
  }

  protected void doTest() throws Exception {
    configureByFile("/generateGetterSetter/before" + getTestName(false) + ".java");
    new GenerateGetterAndSetterHandler() {
      @Override
      protected ClassMember @Nullable [] chooseMembers(ClassMember[] members,
                                                       boolean allowEmptySelection,
                                                       boolean copyJavadocCheckbox,
                                                       Project project,
                                                       @Nullable Editor editor) {
        return members;
      }
    }.invoke(getProject(), getEditor(), getFile());
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    checkResultByFile("/generateGetterSetter/after" + getTestName(false) + ".java");
  }
  
  @NotNull
    @Override
    protected String getTestDataPath() {
      return PluginPathManager.getPluginHomePath("javaFX") + "/testData";
    }
}
