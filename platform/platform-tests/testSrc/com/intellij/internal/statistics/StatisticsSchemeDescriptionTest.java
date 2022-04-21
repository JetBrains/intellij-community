// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics;

import com.intellij.internal.statistic.FUCollectorTestCase;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.fus.reporting.model.lion3.LogEvent;
import junit.framework.TestCase;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class StatisticsSchemeDescriptionTest extends BasePlatformTestCase {

  public <T> void doTestListField(@NotNull EventField<T> field, @NotNull T value, @NotNull List<String> expected) {
    doTestField(field, value, obj -> {
      TestCase.assertNotNull(obj);
      TestCase.assertTrue(obj instanceof List);

      @SuppressWarnings("rawtypes") List<?> resultList = (List)obj;
      for (int i = 0, size = resultList.size(); i < size; i++) {
        Object item = resultList.get(i);
        TestCase.assertEquals(item.toString(), expected.get(i));
      }
      TestCase.assertEquals(resultList.size(), expected.size());
    });
  }

  public <T> void doTestField(@NotNull EventField<T> field, @NotNull T value, @NotNull String expected) {
    doTestField(field, value, obj -> {
      TestCase.assertNotNull(obj);
      TestCase.assertEquals(expected, obj.toString());
    });
  }

  public <T> void doTestField(@NotNull EventField<T> field, @NotNull T value, @NotNull Consumer<Object> validator) {
    EventLogGroup group = new EventLogGroup("group.id", 1);
    EventId1<T> event = group.registerEvent("test.event", field);

    List<LogEvent> events = FUCollectorTestCase.INSTANCE.collectLogEvents(() -> {
      event.log(value);
      return Unit.INSTANCE;
    });

    UsefulTestCase.assertSize(1, events);
    Map<String, Object> eventData = events.get(0).getEvent().getData();
    Object fieldValue = eventData.get(field.getName());
    validator.consume(fieldValue);
  }

  public void testObjectEvent() {
 /* {
      "intField" : 43
      "obj": {
        "name" : "testName",
        "versions" : ["1", "2"]
      }
    } */

    EventLogGroup group = new EventLogGroup("newGroup", 1);
    StringEventField nameField = EventFields.StringValidatedByEnum("name", "os");
    StringListEventField versionsField = EventFields.StringListValidatedByCustomRule("versions", "version");
    IntEventField intEventField = EventFields.Int("intField");
    EventId2<Integer, ObjectEventData> event =
      group.registerEvent("testEvent", intEventField, new ObjectEventField("obj", nameField, versionsField));

    int intValue = 43;
    String testName = "testName";
    List<String> versionsValue = Arrays.asList("1", "2");
    List<LogEvent> events = FUCollectorTestCase.INSTANCE.collectLogEvents(() -> {
      event.log(43, new ObjectEventData(nameField.with("testName"), versionsField.with(Arrays.asList("1", "2"))));
      return Unit.INSTANCE;
    });

    UsefulTestCase.assertSize(1, events);
    Map<String, Object> eventData = events.get(0).getEvent().getData();
    TestCase.assertEquals(intValue, eventData.get("intField"));
    Map<?, ?> objEventData = (Map<?, ?>)eventData.get("obj");
    TestCase.assertEquals(testName, objEventData.get("name"));
    List<?> versions = (List<?>)objEventData.get("versions");
    TestCase.assertEquals(versionsValue, versions);
  }

  public void testLogOnlyRegisteredObjectFields() {
    EventLogGroup group = new EventLogGroup("newGroup", 1);
    StringEventField nameField = EventFields.StringValidatedByEnum("name", "os");
    IntEventField notRegisteredField = EventFields.Int("not_registered");
    EventId1<ObjectEventData> event = group.registerEvent("testEvent", new ObjectEventField("obj", nameField));

    FUCollectorTestCase.INSTANCE.collectLogEvents(() -> {
      assertThrows(IllegalArgumentException.class, "Field not_registered is not in allowed object fields", () ->
        event.log(new ObjectEventData(nameField.with("testName"), notRegisteredField.with(1))));
      return Unit.INSTANCE;
    });
  }

  public void testObjectListEventByFields() {
    EventLogGroup group = new EventLogGroup("newGroup", 1);
    IntEventField countField = EventFields.Int("count");
    StringEventField nameField = EventFields.StringValidatedByEnum("name", "os");
    EventId1<List<? extends ObjectEventData>> event =
      group.registerEvent("testEvent", new ObjectListEventField("objects", nameField, countField));

    List<LogEvent> events = FUCollectorTestCase.INSTANCE.collectLogEvents(() -> {
      ArrayList<ObjectEventData> objects = new ArrayList<>();
      objects.add(new ObjectEventData(nameField.with("testName1"), countField.with(1)));
      objects.add(new ObjectEventData(nameField.with("testName2"), countField.with(2)));
      event.log(objects);
      return Unit.INSTANCE;
    });

    UsefulTestCase.assertSize(1, events);
    Map<String, Object> eventData = events.get(0).getEvent().getData();
    List<?> objects = (List<?>)eventData.get("objects");
    UsefulTestCase.assertSize(2, objects);
  }

  public void testPrimitiveTrueBooleanField() {
    doTestField(EventFields.Boolean("enabled"), true, "true");
  }

  public void testPrimitiveFalseBooleanField() {
    doTestField(EventFields.Boolean("enabled"), false, "false");
  }

  public void testPrimitiveIntField() {
    doTestField(EventFields.Int("count"), 123, "123");
  }

  public void testPrimitiveRoundedIntField() {
    doTestField(EventFields.RoundedInt("rounded_count"), 123, "128");
  }

  public void testPrimitiveNegativeAsRoundedIntField() {
    doTestField(EventFields.RoundedInt("rounded_count"), -1, "-1");
  }

  public void testPrimitiveZeroAsRoundedIntField() {
    doTestField(EventFields.RoundedInt("rounded_count"), 0, "0");
  }

  public void testPrimitiveLongField() {
    doTestField(EventFields.Long("duration_ms"), 123L, "123");
  }

  public void testPrimitiveRoundedLongField() {
    doTestField(EventFields.RoundedLong("rounded_duration_ms"), 60L, "64");
  }

  public void testPrimitiveNegativeAsRoundedLongField() {
    doTestField(EventFields.RoundedLong("rounded_duration_ms"), -1424612L, "-2097152");
  }

  public void testPrimitiveZeroAsRoundedLongField() {
    doTestField(EventFields.RoundedLong("rounded_duration_ms"), 0L, "0");
  }

  public void testPrimitiveDoubleField() {
    doTestField(EventFields.Double("probability"), 0.2, "0.2");
  }

  public void testPrimitiveStringField() {
    doTestField(EventFields.StringValidatedByEnum("type", "os"), "OPENED", "OPENED");
  }

  public void testClassNameField() {
    doTestField(
      EventFields.Class("class_name"),
      TestEventScheme.class,
      "com.intellij.internal.statistics.StatisticsSchemeDescriptionTest$TestEventScheme"
    );
  }

  public void testEnumField() {
    doTestField(
      EventFields.Enum("type", TestEnumEvent.class),
      TestEnumEvent.CLOSED,
      "CLOSED"
    );
  }

  public void testStringListField() {
    doTestListField(
      EventFields.StringListValidatedByCustomRule("errors", "validation_rules"),
      ContainerUtil.newArrayList("foo", "bar"),
      ContainerUtil.newArrayList("foo", "bar")
    );
  }

  public void testLongListField() {
    doTestListField(
      EventFields.LongList("performance"),
      ContainerUtil.newArrayList(123L, 15L, 123456L),
      ContainerUtil.newArrayList("123", "15", "123456")
    );
  }

  private static class TestEventScheme {}

  private enum TestEnumEvent { OPENED, CLOSED }
}
