// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.javaFX.resources.JavaFxResourcePropertyValueInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.jetbrains.plugins.javaFX.fxml.JavaFxFixtureKt.javaFxFixture;
import static org.jetbrains.plugins.javaFX.fxml.JavaFxFixtureKt.javaFxProjectFixture;

@TestApplication
public class JavaFxResourcePropertyValueInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaFxProjectFixture();
  private final TestFixture<JavaFxFixture> javaFx =
    javaFxFixture(projectFixture, PluginPathManager.getPluginHomePath("javaFX") + "/testData/inspections/resourcePropertyValue/",
                  () -> List.of(new JavaFxResourcePropertyValueInspection()));

  @Test
  public void testResourceKeyInEnumAttribute() {
    javaFx.get().getFixture().addFileToProject("messages.properties", """
      correct.alignment=BASELINE_RIGHT
      incorrect.alignment=INCORRECT_VALUE
      """);
    doTest();
  }

  @Test
  public void testResourcePropertySingleFile() {
    javaFx.get().getFixture().addFileToProject("messages.properties", """
      message.width=120
      message.height=not a number
      """);
    doTest();
  }

  @Test
  public void testResourcePropertyMuptipleFiles() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.addFileToProject("messages_de.properties", """
      message.width=120
      message.height=keine Zahl
      message.alignment=CENTER_LEFT
      """);
    // invalid value in both resource files
    // invalid value in one resource file
    myFixture.addFileToProject("messages_fr.properties", """
      message.width=130
      message.height=non un nombre
      message.alignment=valeur non valide
      message.text=le message
      """); // the key exists here, but doesn't in the other file
    doTest();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.testHighlighting(true, false, false, myFixture.getTestName(true) + ".fxml");
  }
}
