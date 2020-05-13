// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics;

import com.intellij.internal.statistic.FUCounterCollectorTestCase;
import com.intellij.internal.statistic.eventLog.*;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import junit.framework.TestCase;
import kotlin.Unit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class StatisticsSchemeDescriptionTest extends BasePlatformTestCase {
  public void testObjectEvent() {
 /* {
      "intField" : 43
      "obj": {
        "name" : "testName",
        "versions" : ["1", "2"]
      }
    } */

    EventLogGroup group = new EventLogGroup("newGroup", 1);
    StringEventField nameField = EventFields.String("name");
    StringListEventField versionsField = EventFields.StringList("versions");
    IntEventField intEventField = EventFields.Int("intField");
    EventId2<Integer, ObjectEventData> event =
      group.registerEvent("testEvent", intEventField, new ObjectEventField("obj", nameField, versionsField));

    int intValue = 43;
    String testName = "testName";
    List<String> versionsValue = Arrays.asList("1", "2");
    List<LogEvent> events = FUCounterCollectorTestCase.INSTANCE.collectLogEvents(() -> {
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
    StringEventField nameField = EventFields.String("name");
    IntEventField notRegisteredField = EventFields.Int("not_registered");
    EventId1<ObjectEventData> event = group.registerEvent("testEvent", new ObjectEventField("obj", nameField));

    FUCounterCollectorTestCase.INSTANCE.collectLogEvents(() -> {
      assertThrows(IllegalArgumentException.class, "Field not_registered is not in allowed object fields", () ->
        event.log(new ObjectEventData(nameField.with("testName"), notRegisteredField.with(1))));
      return Unit.INSTANCE;
    });
  }

  public void testObjectListEventByFields() {
    EventLogGroup group = new EventLogGroup("newGroup", 1);
    IntEventField countField = EventFields.Int("count");
    StringEventField nameField = EventFields.String("name");
    EventId1<List<? extends ObjectEventData>> event =
      group.registerEvent("testEvent", new ObjectListEventField("objects", nameField, countField));

    List<LogEvent> events = FUCounterCollectorTestCase.INSTANCE.collectLogEvents(() -> {
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
}
