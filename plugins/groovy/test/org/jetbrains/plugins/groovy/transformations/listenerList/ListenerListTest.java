// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.listenerList;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;

@TestApplication
public class ListenerListTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("listener.groovy", """
      class WowEvent {}
      interface MyCoolListener {
          void someCoolStuffHappened(WowEvent e)
      }
      """);
  }

  @Test
  public void testAllMethodsResolved() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GroovyFileImpl file = (GroovyFileImpl) myFixture.addFileToProject("Bean.groovy", """
          class Bean {
              @groovy.beans.ListenerList
              List<MyCoolListener> listeners
          }
          """);

    assertFalse(file.isContentsLoaded());

    myFixture.configureByText("_.groovy", """
          def bean = new Bean()
          bean.addMyCoolListener {}
          bean.removeMyCoolListener() {}
          bean.getMyCoolListeners()
          bean.fireSomeCoolStuffHappened(new WowEvent())
          """);
    myFixture.enableInspections(GrUnresolvedAccessInspection.class);
    myFixture.checkHighlighting();
    assertFalse(file.isContentsLoaded());

    myFixture.configureByText("Main.java", """
          class Main {
            void foo() {
              Bean bean = new Bean();
              bean.addMyCoolListener(e -> {});
              bean.removeMyCoolListener(e -> {});
              MyCoolListener[] actionListeners = bean.getMyCoolListeners();
              bean.fireSomeCoolStuffHappened(new WowEvent());
            }
          }
          """);
    myFixture.checkHighlighting();
    assertFalse(file.isContentsLoaded());
  }

  @Test
  public void test_with_empty_custom_name() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Bean.groovy", """
          class Bean {
              @groovy.beans.ListenerList(name = "")
              List<MyCoolListener> listeners
          }
          """);

    myFixture.configureByText("_.groovy", """
          def bean = new Bean()
          bean.addMyCoolListener {}
          bean.removeMyCoolListener() {}
          bean.getMyCoolListeners()
          bean.fireSomeCoolStuffHappened(new WowEvent())
          """);
    myFixture.enableInspections(GrUnresolvedAccessInspection.class);
    myFixture.checkHighlighting();

    myFixture.configureByText("Main.java", """
          class Main {
            void foo() {
              Bean bean = new Bean();
              bean.addMyCoolListener(e -> {});
              bean.removeMyCoolListener(e -> {});
              MyCoolListener[] actionListeners = bean.getMyCoolListeners();
              bean.fireSomeCoolStuffHappened(new WowEvent());
            }
          }
          """);
    myFixture.checkHighlighting();
  }

  @Test
  public void test_with_custom_name() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Bean.groovy", """
          class Bean {
              @groovy.beans.ListenerList(name = "awesomeListener")
              List<MyCoolListener> listeners
          }
          """);

    myFixture.configureByText("_.groovy", """
          def bean = new Bean()
          bean.addAwesomeListener {}
          bean.removeAwesomeListener() {}
          bean.getAwesomeListeners()
          bean.fireSomeCoolStuffHappened(new WowEvent())
          """);
    myFixture.enableInspections(GrUnresolvedAccessInspection.class);
    myFixture.checkHighlighting();

    myFixture.configureByText("Main.java", """
          class Main {
            void foo() {
              Bean bean = new Bean();
              bean.addAwesomeListener(e -> {});
              bean.removeAwesomeListener(e -> {});
              MyCoolListener[] actionListeners = bean.getAwesomeListeners();
              bean.fireSomeCoolStuffHappened(new WowEvent());
            }
          }
          """);
    myFixture.checkHighlighting();
  }

  @Test
  public void test_with_spaces_custom_name() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Bean.groovy", """
          class Bean {
              @groovy.beans.ListenerList(name = "  ")
              List<MyCoolListener> listeners
          }
          """);

    myFixture.configureByText("_.groovy", """
          def bean = new Bean()
          bean.'add  ' {}
          bean.'remove  '() {}
          bean.'get  s'()
          bean.fireSomeCoolStuffHappened(new WowEvent())
          """);
    myFixture.enableInspections(GrUnresolvedAccessInspection.class);
    myFixture.checkHighlighting();
  }

  @Test
  public void test_errors_highlighting() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", """
          import groovy.beans.ListenerList
          class Bean {
            <error descr="@ListenerList field must have a generic Collection type">@ListenerList</error> Object notACollection
            <error descr="@ListenerList field must have a generic Collection type">@ListenerList</error> List rawList
            <error descr="@ListenerList field with generic wildcards not supported">@ListenerList</error> List<?> wildcardList
            <error descr="@ListenerList field with generic wildcards not supported">@ListenerList</error> List<? extends Range> boundedList
            @ListenerList List<Object> okList
          }
          """);
    myFixture.checkHighlighting();
  }
}
