// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.codeInsight;

import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.GenerateSetterHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFixture;
import org.junit.jupiter.api.Test;

import static org.jetbrains.plugins.javaFX.fxml.JavaFxFixtureKt.javaFxFixture;
import static org.jetbrains.plugins.javaFX.fxml.JavaFxFixtureKt.javaFxProjectFixture;

@TestApplication
public class JavaFxGetterSetterPrototypeProviderTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaFxProjectFixture();
  private final TestFixture<JavaFxFixture> javaFx = javaFxFixture(projectFixture);

  @Test
  public void testFinalWritablePropertyGetsSetter() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.configureByText("Foo.java", """
      import javafx.beans.property.SimpleStringProperty;
      class Foo {
          private final SimpleStringProperty name = new SimpleStringProperty();
          <caret>
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      generateSetter();
      myFixture.checkResult("""
        import javafx.beans.property.SimpleStringProperty;
        class Foo {
            private final SimpleStringProperty name = new SimpleStringProperty();

            public void setName(String name) {
                this.name.set(name);
            }
        }
        """);
    });
  }

  private void generateSetter() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    new GenerateSetterHandler() {
      @Override
      protected ClassMember[] chooseMembers(ClassMember[] members,
                                            boolean allowEmptySelection,
                                            boolean copyJavadocCheckbox,
                                            Project project,
                                            @Nullable Editor editor) {
        return members;
      }
    }.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
  }
}
