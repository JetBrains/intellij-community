// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.codeInsight;

import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.GenerateSetterHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.AbstractJavaFXTestCase;

public class JavaFxGetterSetterPrototypeProviderTest extends AbstractJavaFXTestCase {

  public void testFinalWritablePropertyGetsSetter() {
    myFixture.configureByText("Foo.java", """
      import javafx.beans.property.SimpleStringProperty;
      class Foo {
          private final SimpleStringProperty name = new SimpleStringProperty();
          <caret>
      }
      """);
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
  }

  private void generateSetter() {
    new GenerateSetterHandler() {
      @Override
      protected ClassMember[] chooseMembers(ClassMember[] members,
                                            boolean allowEmptySelection,
                                            boolean copyJavadocCheckbox,
                                            Project project,
                                            @Nullable Editor editor) {
        return members;
      }
    }.invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
  }
}
