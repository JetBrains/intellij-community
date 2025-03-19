// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.spock;

import com.intellij.codeInspection.LocalInspectionTool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.List;

public class SpockInteractionsTest extends SpockTestBase implements HighlightingTest {
  @Override
  public @NotNull Collection<Class<? extends LocalInspectionTool>> getInspections() {
    return List.of(
      GroovyAssignabilityCheckInspection.class, GrUnresolvedAccessInspection.class);
  }

  @Before
  public void addClasses() {
    getFixture().addFileToProject("classes.groovy", """
      class Event {}
      
      class Publisher {
        List<Subscriber> subscribers = []
        def send(Event event) {}
      }
      
      interface Subscriber {
        def receive(Event event)
      }
      """);
  }

  @Test
  public void interactions() {
    highlightingTest("""
                       class MySpec extends spock.lang.Specification {
                         def sub1 = Mock(Subscriber)
                         def feature() {
                           sub1.receive<warning descr="'receive' in 'Subscriber' cannot be applied to '(java.lang.String)'">("event")</warning> // not an interaction
                           (1..2) * sub1.receive("event") >> "1"
                           sub1.receive<warning descr="'receive' in 'Subscriber' cannot be applied to '(groovy.lang.Closure<java.lang.Void>)'">({})</warning>
                           1 * sub1.receive({})
                           sub1.receive<warning descr="'receive' in 'Subscriber' cannot be applied to '(java.lang.Object)'">(_)</warning>
                           _ * sub1.receive(_)
                           _ * sub1.receive() >> 1 >>> 2 >> 3 >>> 4
                         }
                       }
                       """);
  }
}
