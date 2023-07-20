// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.serialization

import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataParseException
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataUtils
import com.intellij.testFramework.PlatformTestUtil
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

internal class EventLogMetadataUtilsTest {

  private fun getTestDataRoot() = PlatformTestUtil.getPlatformTestDataPath() + "fus/serialization/"

  @Test
  fun testParseGroupRemoteDescriptorsEmptyContent() {
    assertThrows<EventLogMetadataParseException> {
      EventLogMetadataUtils.parseGroupRemoteDescriptors("")
    }
  }

  @Test
  fun testParseGroupRemoteDescriptorsSpaceContent() {
    assertThrows<EventLogMetadataParseException> {
      EventLogMetadataUtils.parseGroupRemoteDescriptors("   ")
    }
  }

  @Test
  fun testParseGroupRemoteDescriptorsNullContent() {
    assertThrows<EventLogMetadataParseException> {
      EventLogMetadataUtils.parseGroupRemoteDescriptors(null)
    }
  }

  @Test
  fun testParseGroupRemoteDescriptorsInvalidTypeContent() {
    val content = File(getTestDataRoot() + "ParseGroupRemoteDescriptorsInvalidTypeContent.json").readText(Charsets.UTF_8)
    assertThrows<EventLogMetadataParseException> {
      EventLogMetadataUtils.parseGroupRemoteDescriptors(content)
    }
  }

  @Test
  fun testParseGroupRemoteDescriptorsUnrecognizedProperty() {
    val content = File(getTestDataRoot() + "ParseGroupRemoteDescriptorsUnrecognizedProperty.json").readText(Charsets.UTF_8)
    val eventGroupRemoteDescriptors = EventLogMetadataUtils.parseGroupRemoteDescriptors(content)
    assertEquals(1, eventGroupRemoteDescriptors.groups.size)
    assertNull(eventGroupRemoteDescriptors.rules)
    assertNull(eventGroupRemoteDescriptors.version)
  }

  @Test
  fun testParseGroupRemoteDescriptorsException() {
    val content = "{groups : [ { \"id\": \"test.group.id\""
    assertThrows<EventLogMetadataParseException> {
      EventLogMetadataUtils.parseGroupRemoteDescriptors(content)
    }
  }

  @Test
  fun testParseGroupRemoteDescriptorsFullContent() {
    val content = File(getTestDataRoot() + "ParseGroupRemoteDescriptorsFullContent.json").readText(Charsets.UTF_8)
    val eventGroupRemoteDescriptors = EventLogMetadataUtils.parseGroupRemoteDescriptors(content)

    assertEquals(1, eventGroupRemoteDescriptors.groups.size)
    val group = eventGroupRemoteDescriptors.groups[0]
    assertEquals("test.group.id", group.id)

    assertNotNull(group.builds)
    assertEquals(1, group.builds?.size)
    val build = group.builds?.get(0)
    assertNotNull(build)
    assertEquals("191.6873", build?.from)
    assertEquals("192.6873", build?.to)

    assertNotNull(group.versions)
    val version = group.versions?.get(0)
    assertNotNull(version)
    assertEquals("1", version?.from)
    assertEquals("2", version?.to)

    assertNotNull(group.rules)
    assertNotNull(group.rules?.event_id)
    assertEquals(1, group.rules?.event_id?.size)
    assertEquals(true, group.rules?.event_id?.contains("{enum:screen.reader.detected|screen.reader.support.enabled}"))

    assertNotNull(group.rules?.event_data)
    assertEquals(1, group.rules?.event_data?.size)
    assertEquals(true, group.rules?.event_data?.contains("enabled"))
    val enabled = group.rules?.event_data?.get("enabled")
    assertEquals(true, enabled?.contains("{enum#boolean}"))

    assertNotNull(group.rules?.enums)
    assertEquals(1, group.rules?.enums?.size)
    assertEquals(true, group.rules?.enums?.contains("setting"))
    val enums = group.rules?.enums?.get("setting")
    assertNotNull(enums)
    assertEquals(2, enums?.size)
    assertEquals(true, enums?.contains("isCollapseFinishedTargets"))
    assertEquals(true, enums?.contains("isColoredOutputMessages"))

    assertNotNull(group.rules?.regexps)
    assertEquals(1, group.rules?.regexps?.size)
    assertEquals(true, group.rules?.regexps?.contains("permission"))
    assertEquals("-?[0-9]{1,3}", group.rules?.regexps?.get("permission"))

    assertNotNull(group.anonymized_fields)
    assertEquals(1, group.anonymized_fields?.size)
    val anonymized_fields = group.anonymized_fields?.get(0)
    assertNotNull(anonymized_fields)
    assertEquals("close", anonymized_fields?.event)
    assertEquals(1, anonymized_fields?.fields?.size)
    assertEquals(true, anonymized_fields?.fields?.contains("field"))

    assertNotNull(eventGroupRemoteDescriptors.rules)
    assertNotNull(eventGroupRemoteDescriptors.rules?.enums)
    assertEquals(2, eventGroupRemoteDescriptors.rules?.enums?.size)

    assertNotNull(eventGroupRemoteDescriptors.rules?.regexps)
    assertEquals(3, eventGroupRemoteDescriptors.rules?.regexps?.size)

    assertNull(eventGroupRemoteDescriptors.rules?.event_id)
    assertNull(eventGroupRemoteDescriptors.rules?.event_data)

    assertEquals("1", eventGroupRemoteDescriptors.version)
  }

  @Test
  fun testParseGroupRemoteDescriptorsRealMetadata() {
    val content = File(getTestDataRoot() + "ParseGroupRemoteDescriptorsRealData.json").readText(Charsets.UTF_8)
    val eventGroupRemoteDescriptors = EventLogMetadataUtils.parseGroupRemoteDescriptors(content)
    assertEquals(3, eventGroupRemoteDescriptors.groups.size)
    assertNotNull(eventGroupRemoteDescriptors.rules)
    assertNull(eventGroupRemoteDescriptors.rules?.event_id)
    assertNull(eventGroupRemoteDescriptors.rules?.event_data)
    assertNotNull(eventGroupRemoteDescriptors.rules?.enums)
    assertEquals(4, eventGroupRemoteDescriptors.rules?.enums?.size)
    assertNotNull(eventGroupRemoteDescriptors.rules?.regexps)
    assertEquals(1, eventGroupRemoteDescriptors.rules?.regexps?.size)
    assertEquals("2677", eventGroupRemoteDescriptors.version)
  }
}