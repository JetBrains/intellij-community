// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.listenerList;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;
import org.junit.Assert;

public class ListenerListTest extends LightGroovyTestCase {
  @Override
  @NotNull
  public final  LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    getFixture().addFileToProject("listener.groovy", """
      class WowEvent {}
      interface MyCoolListener {
          void someCoolStuffHappened(WowEvent e)
      }
      """);
  }

  public void testAllMethodsResolved() {
    GroovyFileImpl file = (GroovyFileImpl) myFixture.addFileToProject("Bean.groovy", """
          class Bean {
              @groovy.beans.ListenerList
              List<MyCoolListener> listeners
          }
          """);

    Assert.assertFalse(file.isContentsLoaded());

    myFixture.configureByText("_.groovy", """
          def bean = new Bean()
          bean.addMyCoolListener {}
          bean.removeMyCoolListener() {}
          bean.getMyCoolListeners()
          bean.fireSomeCoolStuffHappened(new WowEvent())
          """);
    myFixture.enableInspections(GrUnresolvedAccessInspection.class);
    myFixture.checkHighlighting();
    Assert.assertFalse(file.isContentsLoaded());

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
    Assert.assertFalse(file.isContentsLoaded());
  }

  public void test_with_empty_custom_name() {
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

  public void test_with_custom_name() {
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

  public void test_with_spaces_custom_name() {
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

  public void test_errors_highlighting() {
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
