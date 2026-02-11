// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references

import com.intellij.lang.properties.psi.Property
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import org.jetbrains.idea.devkit.inspections.EventLogDescriptionInspection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.api.assertNotNull

class EventLogDescriptionReferenceContributorTest : LightJavaCodeInsightFixtureTestCase5() {
  @BeforeEach
  fun setUp() {
    fixture.addClass("""
      package com.intellij.internal.statistic.eventLog;
      public class EventLogGroup {
        public EventLogGroup(String id, int version) {}
        public EventLogGroup(String id, int version, String recorder) {}
        public EventId registerEvent(String id) {}
        public EventId registerVarargEvent(String id, Object... fields) {}
        public static class EventId {}
      }""".trimIndent()
    )
    fixture.addFileToProject("build/events/FUS.properties", """
      my.group=FUS group
      my.group.event=FUS event
      my.group.var.event=FUS vararg event
      empty.group=
      my.group.empty.event=
    """.trimIndent())
    fixture.addFileToProject("build/events/ML.properties", "ml.group=ML group")
  }

  @Test
  fun `group in default recorder - Java`() {
    fixture.configureByText("Foo.java", """
      import com.intellij.internal.statistic.eventLog.EventLogGroup;
      class Foo {
        private static final EventLogGroup GROUP = new EventLogGroup("my.group<caret>", 1);
      }
      """.trimIndent()
    )
    testResolve("FUS.properties", "FUS group")
  }

  @Test
  fun `event in default recorder - Java`() {
    fixture.configureByText("Foo.java", """
      import com.intellij.internal.statistic.eventLog.EventLogGroup;
      class Foo {
        private static final EventLogGroup GROUP = new EventLogGroup("my.group", 1);
        private static final EventLogGroup.EventId EVENT = GROUP.registerEvent("event<caret>");
      }
      """.trimIndent()
    )
    testResolve("FUS.properties", "FUS event")
  }

  @Test
  fun `vararg event in default recorder - Java`() {
    fixture.configureByText("Foo.java", """
      import com.intellij.internal.statistic.eventLog.EventLogGroup;
      class Foo {
        private static final EventLogGroup GROUP = new EventLogGroup("my.group", 1);
        private static final EventLogGroup.EventId EVENT = GROUP.registerVarargEvent("var.event<caret>", "whatever");
      }
      """.trimIndent()
    )
    testResolve("FUS.properties", "FUS vararg event")
  }

  @Test
  fun `group in default recorder - Kotlin`() {
    fixture.configureByText("Foo.kt", """
      import com.intellij.internal.statistic.eventLog.EventLogGroup
      object Foo {
        private val GROUP = EventLogGroup("my.group<caret>", 1)
      }
      """.trimIndent()
    )
    testResolve("FUS.properties", "FUS group")
  }

  @Test
  fun `event in default recorder - Kotlin`() {
    fixture.configureByText("Foo.kt", """
      import com.intellij.internal.statistic.eventLog.EventLogGroup
      object Foo {
        val GROUP = EventLogGroup("my.group", 1)  // public, to verify the Kotlin property accessor way
        val EVENT = GROUP.registerEvent("event<caret>")
      }
      """.trimIndent()
    )
    testResolve("FUS.properties", "FUS event")
  }

  @Test
  fun `vararg event in default recorder - Kotlin`() {
    fixture.configureByText("Foo.kt", """
      import com.intellij.internal.statistic.eventLog.EventLogGroup
      object Foo {
        private val GROUP = EventLogGroup("my.group", 1)
        private val EVENT = GROUP.registerVarargEvent("var.event<caret>", "whatever")
      }
      """.trimIndent()
    )
    testResolve("FUS.properties", "FUS vararg event")
  }

  @Test
  fun `group in custom recorder`() {
    fixture.configureByText("Foo.java", """
      import com.intellij.internal.statistic.eventLog.EventLogGroup;
      class Foo {
        private static final String RECORDER = "ML";
        private static final EventLogGroup GROUP = new EventLogGroup("ml.group<caret>", 1, RECORDER);
      }""".trimIndent()
    )
    testResolve("ML.properties", "ML group")
  }

  @Test
  fun `skipping navigation when recorder is non-evaluatable`() {
    fixture.configureByText("Foo.java", """
      import com.intellij.internal.statistic.eventLog.EventLogGroup;
      class Foo {
        private static String recorder() { return "ML"; }
        private static final EventLogGroup GROUP = new EventLogGroup("ml.group<caret>", 1, recorder());
      }""".trimIndent()
    )

    testNoResolve()
  }

  @Test
  fun `problem highlighting - Java`() {
    fixture.enableInspections(EventLogDescriptionInspection::class.java)
    fixture.configureByText("Foo.java", """
      import com.intellij.internal.statistic.eventLog.EventLogGroup;
      class Foo {
        // the good
        private static final EventLogGroup GROUP = new EventLogGroup("my.group", 1);
        private static final EventLogGroup.EventId EVENT = GROUP.registerEvent("event");
        // the bad
        private static final EventLogGroup G1 =
          new EventLogGroup("-", 1, <warning descr="Cannot evaluate the recorder name; consider using a constant expression">System.getProperty("recorder")</warning>);
        private static final EventLogGroup G2 =
          new EventLogGroup("-", 1, <error descr="Unknown recorder ('build/events/WTF.properties' is missing)">"WTF"</error>);
        private static final EventLogGroup G3 =
          new EventLogGroup(<warning descr="Cannot evaluate the group ID; consider using a constant expression">System.getProperty("group")</warning>, 1, "FUS");
        private static final EventLogGroup G4 =
          new EventLogGroup(<error descr="Group 'missing.group' description is missing from 'FUS.properties'">"missing.group"</error>, 1);
        private static final EventLogGroup G5 =
          new EventLogGroup(<error descr="Group 'empty.group' description is empty">"empty.group"</error>, 1);
        // the ugly
        private static EventLogGroup group() { return new EventLogGroup("my.group", 1); }
        private static final EventLogGroup.EventId E1 =
          <warning descr="Cannot find the event group; consider using a constant expression">group()</warning>.registerEvent("-");
        private static final EventLogGroup.EventId E2 =
          GROUP.registerEvent(<warning descr="Cannot evaluate the event ID; consider using a constant expression">System.getProperty("event")</warning>);
        private static final EventLogGroup.EventId E3 =
          GROUP.registerEvent(<error descr="Event 'my.group.missing.event' description is missing from 'FUS.properties'">"missing.event"</error>);
        private static final EventLogGroup.EventId E4 =
          GROUP.registerEvent(<error descr="Event 'my.group.empty.event' description is empty">"empty.event"</error>);
      }""".trimIndent()
    )
    fixture.checkHighlighting()
  }

  @Test
  fun `problem highlighting - Kotlin`() {
    fixture.enableInspections(EventLogDescriptionInspection::class.java)
    fixture.configureByText("Foo.kt", """
      import com.intellij.internal.statistic.eventLog.EventLogGroup
      object Foo {
        // the good
        private val GROUP = EventLogGroup("my.group", 1)
        private val EVENT = GROUP.registerEvent("event")
        // the bad
        private val G1 = EventLogGroup("-", 1, <warning descr="Cannot evaluate the recorder name; consider using a constant expression">System.getProperty("recorder")</warning>)
        private val G2 = EventLogGroup("-", 1, <error descr="Unknown recorder ('build/events/WTF.properties' is missing)">"WTF"</error>)
        private val G3 = EventLogGroup(<warning descr="Cannot evaluate the group ID; consider using a constant expression">System.getProperty("group")</warning>, 1, "FUS")
        private val G4 = EventLogGroup(<error descr="Group 'missing.group' description is missing from 'FUS.properties'">"missing.group"</error>, 1)
        private val G5 = EventLogGroup(<error descr="Group 'empty.group' description is empty">"empty.group"</error>, 1)
        // the ugly
        private fun group() = EventLogGroup("my.group", 1)
        private val E1 = <warning descr="Cannot find the event group; consider using a constant expression">group()</warning>.registerEvent("-")
        private val E2 = GROUP.registerEvent(<warning descr="Cannot evaluate the event ID; consider using a constant expression">System.getProperty("event")</warning>)
        private val E3 = GROUP.registerEvent(<error descr="Event 'my.group.missing.event' description is missing from 'FUS.properties'">"missing.event"</error>)
        private val E4 = GROUP.registerEvent(<error descr="Event 'my.group.empty.event' description is empty">"empty.event"</error>)
      }""".trimIndent()
    )
    fixture.checkHighlighting()
  }

  private fun testResolve(expectedFile: String, expectedText: String) = runReadAction {
    val reference = fixture.getReferenceAtCaretPosition()
    assertNotNull(reference)
    assertEquals("EventLogDescriptionReference", reference.javaClass.simpleName)
    val property = assertInstanceOf<Property>(reference.resolve())
    assertEquals(expectedFile, property.containingFile.name)
    assertEquals(expectedText, property.value)
  }

  private fun testNoResolve() {
    val reference = fixture.getReferenceAtCaretPosition()
    if (reference != null) {
      assertNotEquals("EventLogDescriptionReference", reference.javaClass.simpleName)
    }
  }
}
