// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.metadata.validator

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.eventLog.validator.rules.impl.LocalEnumCustomValidationRule
import com.intellij.internal.statistic.eventLog.validator.storage.ValidationRulesPersistedStorage
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.util.Disposer
import junit.framework.TestCase
import org.junit.Assert
import org.junit.Test
import java.util.*

@Suppress("SameParameterValue")
class SensitiveDataValidatorTest : BaseSensitiveDataValidatorTest() {

  @Test
  fun test_empty_rule() {
    val validator = newValidatorByFile("test_empty_rule.json")
    val eventLogGroup = EventLogGroup("build.gradle.actions", 1)

    assertEmpty(validator.getEventRules(eventLogGroup))
    assertTrue(validator.getEventDataRules(eventLogGroup).isEmpty())

    assertUndefinedRule(validator, eventLogGroup, "<any-string-accepted>")
    assertEventDataNotAccepted(validator, eventLogGroup, ValidationResultType.UNDEFINED_RULE, ValidationResultType.UNDEFINED_RULE.description, "<any-string-accepted>")
  }

  @Test
  fun test_simple_enum_rules() {
    val validator = newValidatorByFile("test_simple_enum_rules.json")
    var elg = EventLogGroup("my.simple.enum.value", 1)

    assertEventAccepted(validator, elg, "AAA")
    assertEventAccepted(validator, elg, "BBB")
    assertEventAccepted(validator, elg, "CCC")
    assertEventRejected(validator, elg, "ABC")

    elg = EventLogGroup("my.simple.enum.node.value", 1)
    assertEventAccepted(validator, elg, "NODE_AAA")
    assertEventAccepted(validator, elg, "NODE_BBB")
    assertEventAccepted(validator, elg, "NODE_CCC")
    assertEventRejected(validator, elg, "NODE_ABC")

    elg = EventLogGroup("my.simple.enum.ref", 1)
    assertEventAccepted(validator, elg, "REF_AAA")
    assertEventAccepted(validator, elg, "REF_BBB")
    assertEventAccepted(validator, elg, "REF_CCC")
    assertEventRejected(validator, elg, "REF_ABC")

    elg = EventLogGroup("my.simple.enum.node.ref", 1)
    assertEventAccepted(validator, elg, "NODE_REF_AAA")
    assertEventAccepted(validator, elg, "NODE_REF_BBB")
    assertEventAccepted(validator, elg, "NODE_REF_CCC")
    assertEventRejected(validator, elg, "NODE_REF_ABC")
  }

  @Test
  fun test_simple_enum_rules_with_spaces() {
    // enum values: ["NODE_REF_AAA", "NODE_REF_BBB", "NODE_REF_CCC"]
    val validator = newValidatorByFile("test_simple_enum_rules.json")

    val elg = EventLogGroup("my.simple.enum.node.ref", 1)
    assertEventAccepted(validator, elg, "NODE REF AAA")
    assertEventAccepted(validator, elg, "NOD'E;REF:BBB")
    assertEventAccepted(validator, elg, "NODE;REF:BBB")
    assertEventAccepted(validator, elg, "NODE_REF_BBB")
    assertEventAccepted(validator, elg, "NODE,REF,BBB")
    assertEventAccepted(validator, elg, "NOD'E_REF_BBB")
    assertEventAccepted(validator, elg, "NOD'E;REF_BBB")
    assertEventAccepted(validator, elg, "NOD'E:REF;BBB")
    assertEventAccepted(validator, elg, "NO\"DE REF CCC")

    assertEventRejected(validator, elg, "NODEREFCCC")
  }

  @Test
  fun test_simple_regexp_rules() {
    // custom regexp is:   (.+)\s*:\s*(.*)  => matches  'aaa/java.lang.String'
    val validator = newValidatorByFile("test_simple_regexp_rules.json")

    var elg = EventLogGroup("my.simple.regexp.value", 1)
    assertEventAccepted(validator, elg, "aaa/java.lang.String")
    assertEventRejected(validator, elg, "java.lang.String")

     elg = EventLogGroup("my.simple.regexp.node.value", 1)
    assertEventAccepted(validator, elg, "aaa/java.lang.String")
    assertEventRejected(validator, elg, "java.lang.String")

    elg = EventLogGroup("my.simple.regexp.ref", 1)
    assertEventAccepted(validator, elg, "aaa/java.lang.String")
    assertEventRejected(validator, elg, "java.lang.String")

    elg = EventLogGroup("my.simple.regexp.node.ref", 1)
    assertEventAccepted(validator, elg, "aaa/java.lang.String")
    assertEventRejected(validator, elg, "java.lang.String")

    elg = EventLogGroup("my.simple.regexp.with.number.of.elements", 1)
    assertEventAccepted(validator, elg, "0512345678ABCD023543")
    assertEventAccepted(validator, elg, "1154265567ABCD-23-43")
    assertEventAccepted(validator, elg, "0512345678QWER012-43")
    assertEventAccepted(validator, elg, "9965430987ASDF-01003")
    assertEventRejected(validator, elg, "aa65430987ASDF-01003")
    assertEventRejected(validator, elg, "999965430987ASDF-01003")
  }

  @Test
  fun test_global_integer_regex_rule() {
    val validator = newValidatorByFile("test_global_regexp_rules.json")

    val elg = EventLogGroup("regex.int.rule.group", 1)
    var value = 1000
    while (value > 0) {
      assertEventAccepted(validator, elg, value.toString())
      assertEventAccepted(validator, elg, (-1 * value).toString())
      value /= 2
    }
    assertEventAccepted(validator, elg, value.toString())
  }

  @Test
  fun test_global_double_regex_rule() {
    val validator = newValidatorByFile("test_global_regexp_rules.json")

    val elg = EventLogGroup("regex.double.rule.group", 1)
    var value = 100.0
    while (value > 0.00001) {
      assertEventAccepted(validator, elg, value.toString())
      assertEventAccepted(validator, elg, (-1 * value).toString())
      value /= 2
    }

    value = 1000000.0
    while (value < 100000000.0) {
      assertEventAccepted(validator, elg, value.toString())
      assertEventAccepted(validator, elg, (-1 * value).toString())
      value *= 2
    }
  }

  @Test
  fun test_simple_regexp_rules_with_spaces() {
    // custom regexp is:   [AB]_(.*) => matches  'A_x', 'A x'
    val validator = newValidatorByFile("test_simple_regexp_rules.json")

    val elg = EventLogGroup("my.simple.regexp.with.underscore", 1)
    assertEventAccepted(validator, elg, "A_x")
    assertEventAccepted(validator, elg, "A;x")
    assertEventAccepted(validator, elg, "A,x")
    assertEventAccepted(validator, elg, "A:x")
    assertEventAccepted(validator, elg, "A x")
    assertEventAccepted(validator, elg, "B:x")
    assertEventAccepted(validator, elg, "B;x")
    assertEventAccepted(validator, elg, "B_x")
    assertEventAccepted(validator, elg, "B x")
    assertEventAccepted(validator, elg, "B  x")
    assertEventAccepted(validator, elg, "B:_x")
    assertEventAccepted(validator, elg, "B:;x")
    assertEventAccepted(validator, elg, "B,,x")
    assertEventAccepted(validator, elg, "B__x")
    assertEventRejected(validator, elg, "Bxx")
  }

  @Test
  fun test_simple_expression_rules() {
    // custom expression is:   "JUST_TEXT[_{regexp:\\d+(\\+)?}_]_xxx_{enum:AAA|BBB|CCC}_zzz{enum#myEnum}_yyy"
    val validator = newValidatorByFile("test_simple_expression_rules.json")
    var elg = EventLogGroup("my.simple.expression", 1)

    assertSize(1, validator.getEventRules(elg))

    assertEventAccepted(validator, elg, "JUST_TEXT[_123456_]_xxx_CCC_zzzREF_AAA_yyy")
    assertEventRejected(validator, elg, "JUST_TEXT[_FOO_]_xxx_CCC_zzzREF_AAA_yyy")
    assertEventRejected(validator, elg, "")

    //  {enum:AAA|}foo
    elg = EventLogGroup("my.simple.enum.node.with.empty.value", 1)
    assertEventAccepted(validator, elg, "AAAfoo")
    assertEventAccepted(validator, elg, "foo")
    assertEventRejected(validator, elg, " foo")
    assertEventRejected(validator, elg, " AAA foo")
  }

  @Test
  fun test_simple_expression_rules_with_spaces() {
    // custom expression is:   "JUST_TEXT[_{regexp:\\d+(\\+)?}_]_xxx_{enum:AAA|BBB|CCC}_zzz{enum#myEnum}_yyy"
    val validator = newValidatorByFile("test_simple_expression_rules.json")
    val elg = EventLogGroup("my.simple.expression", 1)

    assertEventAccepted(validator, elg, "JUST_TEXT[_123456_]:xxx_CCC_zzzREF_AAA_yyy")
    assertEventAccepted(validator, elg, "JUST_TEXT[_123456_]_xxx;CCC_zzzREF_AAA_yyy")
    assertEventAccepted(validator, elg, "JUST_TEXT[_123456_]:xxx,CCC_zzzREF_AAA_yyy")
    assertEventAccepted(validator, elg, "JUST_TEXT[_123456_]_xxx_CCC_zzzREF_AAA_yyy")
    assertEventAccepted(validator, elg, "JUST TEXT[_123456_]_xxx CCC,zzzREF:AAA;yyy")
    assertEventRejected(validator, elg, "JUSTTEXT[_123456_]_xxx!CCC_zzzREF:AAA;yyy")
  }

  @Test
  fun test_regexp_rule_with_global_regexps() {
    val validator = newValidatorByFile("test_regexp_rule-with-global-regexp.json")
    val elg = EventLogGroup("ui.fonts", 1)

    assertSize(10, validator.getEventRules(elg))
    assertTrue(validator.getEventDataRules(elg).isEmpty())

    assertEventAccepted(validator, elg, "Presentation.mode.font.size[24]")
    assertEventAccepted(validator, elg, "IDE.editor.font.name[Monospaced]")
    assertEventAccepted(validator, elg, "IDE.editor.font.name[DejaVu_Sans_Mono]")
    assertEventAccepted(validator, elg, "Console.font.size[10]")

    assertEventRejected(validator, elg, "foo")
  }

  @Test
  fun test_validate_system_event_data() {
    val validator = newValidatorByFile("test_validate_event_data.json")
    val elg = EventLogGroup("system.keys.group", 1)

    val platformDataKeys: List<String> =
      listOf("plugin", "project", "os", "plugin_type", "lang", "current_file", "input_event", "place")

    for (platformDataKey in platformDataKeys) {
      assertEventDataAccepted(validator, elg, platformDataKey, "<validated>")
    }
    assertEventDataAccepted(validator, elg, "ed_1", "AA")
    assertEventDataAccepted(validator, elg, "ed_2", "REF_BB")
    assertEventDataNotAccepted(validator, elg, ValidationResultType.REJECTED, "ed_1", "CC")
    assertEventDataNotAccepted(validator, elg, ValidationResultType.REJECTED, "ed_2", "REF_XX")
    assertEventDataNotAccepted(validator, elg, ValidationResultType.UNDEFINED_RULE, ValidationResultType.UNDEFINED_RULE.description, "<unknown>")
  }

  @Test
  fun test_validate_escaped_event_data() {
    val validator = newValidatorByFile("test_validate_event_data.json")
    val elg = EventLogGroup("system.keys.group", 1)

    assertEventDataAccepted(validator, elg, "ed.1", "AA")
    assertEventDataAccepted(validator, elg, "ed 2", "REF_BB")
    assertEventDataAccepted(validator, elg, "ed_1", "AA")
    assertEventDataAccepted(validator, elg, "ed_2", "REF_BB")
    assertEventDataNotAccepted(validator, elg, ValidationResultType.UNDEFINED_RULE, ValidationResultType.UNDEFINED_RULE.description, "REF_BB")
  }

  @Test
  fun test_validate_custom_rule_with_local_enum() {
    val rule = TestLocalEnumCustomValidationRule()

    Assert.assertEquals(ValidationResultType.ACCEPTED, rule.validate("FIRST", EventContext.create("FIRST", emptyMap())))
    Assert.assertEquals(ValidationResultType.ACCEPTED, rule.validate("SECOND", EventContext.create("FIRST", emptyMap())))
    Assert.assertEquals(ValidationResultType.ACCEPTED, rule.validate("THIRD", EventContext.create("FIRST", emptyMap())))

    Assert.assertEquals(ValidationResultType.REJECTED, rule.validate("FORTH", EventContext.create("FIRST", emptyMap())))
    Assert.assertEquals(ValidationResultType.REJECTED, rule.validate("", EventContext.create("FIRST", emptyMap())))
    Assert.assertEquals(ValidationResultType.REJECTED, rule.validate("UNKNOWN", EventContext.create("FIRST", emptyMap())))
  }

  @Test
  fun test_joined_string_validation() {
    val validator = newValidatorByFile("test_join_string_regexp_rule.json")
    val group = EventLogGroup("my.test.rule.with.long.string", 1)

    val toAccept =
      arrayOf("r50", "q50", "p", "p45", "p451", "k50", "l", "l50", "m11", "m40", "m403", "m5.0", "t", "t45", "t451",
              "g1.0", "g1.1", "g2.0", "g2.2", "g3.0", "g3.1", "f", "e1.0", "e1.3", "e1.6", "e2.0", "e2.1",
              "h-m5.0", "h-b12", "h-m40+d4", "h-m4.5+a8", "h-m4.5+y8.2", "h-m40+d5+a8", "h-a81+y81+w81",
              "h-m40+d5+a8+w81", "h-m40+d5+a8+w81+y8", "h-m40+a8+d4+y7", "h-m45+d5+a8+w81+y8",
              "d4", "d5", "c3", "c4", "b", "b10.0", "a", "a10", "a81", "x", "y", "y75", "y8", "w81")

    for (value in toAccept) {
      assertEventAccepted(validator, group, value)
    }
    assertEventRejected(validator, group, "8")
    assertEventRejected(validator, group, "h-")
  }

  fun test_validate_event_id_with_enum_and_existing_rule() {
    doTestWithRuleList("test_rules_list_event_id.json") { validator ->
      val elg = EventLogGroup("enum.and.existing.util.rule", 1)

      assertSize(2, validator.getEventRules(elg))

      assertEventAccepted(validator, elg, "AAA")
      assertEventAccepted(validator, elg, "BBB")
      assertEventAccepted(validator, elg, "CCC")

      assertEventRejected(validator, elg, "DDD")
      assertEventAccepted(validator, elg, "FIRST")
    }
  }

  fun test_validate_event_id_with_enum_and_not_existing_rule() {
    doTestWithRuleList("test_rules_list_event_id.json") { validator ->
      val elg = EventLogGroup("enum.and.not.existing.util.rule", 1)

      assertSize(2, validator.getEventRules(elg))

      assertEventAccepted(validator, elg, "AAA")
      assertEventAccepted(validator, elg, "BBB")
      assertEventAccepted(validator, elg, "CCC")

      assertIncorrectRule(validator, elg, "DDD")
      assertIncorrectRule(validator, elg, "FIRST")
    }
  }

  fun test_validate_event_id_with_enum_and_third_party_rule() {
    doTestWithRuleList("test_rules_list_event_id.json") { validator ->
      val elg = EventLogGroup("enum.and.third.party.util.rule", 1)

      assertSize(2, validator.getEventRules(elg))

      assertEventAccepted(validator, elg, "AAA")
      assertEventAccepted(validator, elg, "BBB")
      assertEventAccepted(validator, elg, "CCC")

      assertThirdPartyRule(validator, elg, "DDD")
      assertEventAccepted(validator, elg, "FIRST")
      assertThirdPartyRule(validator, elg, "SECOND")
    }
  }

  fun test_validate_event_id_with_existing_rule_and_enum() {
    doTestWithRuleList("test_rules_list_event_id.json") { validator ->
      val elg = EventLogGroup("existing.util.rule.and.enum", 1)

      assertSize(4, validator.getEventRules(elg))

      assertEventAccepted(validator, elg, "AAA")
      assertEventAccepted(validator, elg, "BBB")
      assertEventAccepted(validator, elg, "CCC")

      assertEventRejected(validator, elg, "DDD")
      assertEventAccepted(validator, elg, "FIRST")
    }
  }

  fun test_validate_event_id_with_not_existing_rule_and_enum() {
    doTestWithRuleList("test_rules_list_event_id.json") { validator ->
      val elg = EventLogGroup("not.existing.util.rule.and.enum", 1)

      assertSize(4, validator.getEventRules(elg))

      assertEventAccepted(validator, elg, "AAA")
      assertEventAccepted(validator, elg, "BBB")
      assertEventAccepted(validator, elg, "CCC")

      assertEventRejected(validator, elg, "DDD")
      assertEventRejected(validator, elg, "FIRST")
    }
  }

  fun test_validate_event_id_with_third_party_rule_and_enum() {
    doTestWithRuleList("test_rules_list_event_id.json") { validator ->
      val elg = EventLogGroup("third.party.util.rule.and.enum", 1)

      assertSize(4, validator.getEventRules(elg))

      assertEventAccepted(validator, elg, "AAA")
      assertEventAccepted(validator, elg, "BBB")
      assertEventAccepted(validator, elg, "CCC")

      assertEventRejected(validator, elg, "DDD")
      assertEventAccepted(validator, elg, "FIRST")
      assertEventRejected(validator, elg, "SECOND")
    }
  }

  fun test_validate_event_data_with_enum_and_existing_rule() {
    doTestWithRuleList("test_rules_list_event_data.json") { validator ->
      val elg = EventLogGroup("enum.and.existing.util.rule", 1)

      val dataRules = validator.getEventDataRules(elg)
      assertSize(2, dataRules["data_1"] ?: error("Cannot find rules for 'data_1' field"))

      assertEventDataAccepted(validator, elg, "data_1", "AAA")
      assertEventDataAccepted(validator, elg, "data_1", "BBB")
      assertEventDataAccepted(validator, elg, "data_1", "CCC")

      assertEventDataNotAccepted(validator, elg, ValidationResultType.REJECTED, "data_1", "DDD")
      assertEventDataAccepted(validator, elg, "data_1", "FIRST")
    }
  }

  fun test_validate_event_data_with_enum_and_not_existing_rule() {
    doTestWithRuleList("test_rules_list_event_data.json") { validator ->
      val elg = EventLogGroup("enum.and.not.existing.util.rule", 1)

      val dataRules = validator.getEventDataRules(elg)
      assertSize(2, dataRules["data_1"] ?: error("Cannot find rules for 'data_1' field"))

      assertEventDataAccepted(validator, elg, "data_1", "AAA")
      assertEventDataAccepted(validator, elg, "data_1", "BBB")
      assertEventDataAccepted(validator, elg, "data_1", "CCC")

      assertEventDataNotAccepted(validator, elg, ValidationResultType.INCORRECT_RULE, "data_1", "DDD")
      assertEventDataNotAccepted(validator, elg, ValidationResultType.INCORRECT_RULE, "data_1", "FIRST")
    }
  }

  fun test_validate_event_data_with_enum_and_third_party_rule() {
    doTestWithRuleList("test_rules_list_event_data.json") { validator ->
      val elg = EventLogGroup("enum.and.third.party.util.rule", 1)

      val dataRules = validator.getEventDataRules(elg)
      assertSize(2, dataRules["data_1"] ?: error("Cannot find rules for 'data_1' field"))

      assertEventDataAccepted(validator, elg, "data_1", "AAA")
      assertEventDataAccepted(validator, elg, "data_1", "BBB")
      assertEventDataAccepted(validator, elg, "data_1", "CCC")

      assertEventDataNotAccepted(validator, elg, ValidationResultType.THIRD_PARTY, "data_1", "DDD")
      assertEventDataAccepted(validator, elg, "data_1", "FIRST")
      assertEventDataNotAccepted(validator, elg, ValidationResultType.THIRD_PARTY, "data_1", "SECOND")
    }
  }

  fun test_validate_event_data_with_existing_rule_and_enum() {
    doTestWithRuleList("test_rules_list_event_data.json") { validator ->
      val elg = EventLogGroup("existing.util.rule.and.enum", 1)

      val dataRules = validator.getEventDataRules(elg)
      assertSize(4, dataRules["data_1"] ?: error("Cannot find rules for 'data_1' field"))

      assertEventDataAccepted(validator, elg, "data_1", "AAA")
      assertEventDataAccepted(validator, elg, "data_1", "BBB")
      assertEventDataAccepted(validator, elg, "data_1", "CCC")

      assertEventDataNotAccepted(validator, elg, ValidationResultType.REJECTED, "data_1", "DDD")
      assertEventDataAccepted(validator, elg, "data_1", "FIRST")
    }
  }

  fun test_validate_event_data_with_not_existing_rule_and_enum() {
    doTestWithRuleList("test_rules_list_event_data.json") { validator ->
      val elg = EventLogGroup("not.existing.util.rule.and.enum", 1)

      val dataRules = validator.getEventDataRules(elg)
      assertSize(4, dataRules["data_1"] ?: error("Cannot find rules for 'data_1' field"))

      assertEventDataAccepted(validator, elg, "data_1", "AAA")
      assertEventDataAccepted(validator, elg, "data_1", "BBB")
      assertEventDataAccepted(validator, elg, "data_1", "CCC")

      assertEventDataNotAccepted(validator, elg, ValidationResultType.REJECTED, "data_1", "DDD")
      assertEventDataNotAccepted(validator, elg, ValidationResultType.REJECTED, "data_1", "FIRST")
    }
  }

  fun test_validate_event_data_with_third_party_rule_and_enum() {
    doTestWithRuleList("test_rules_list_event_data.json") { validator ->
      val elg = EventLogGroup("third.party.util.rule.and.enum", 1)

      val dataRules = validator.getEventDataRules(elg)
      assertSize(4, dataRules["data_1"] ?: error("Cannot find rules for 'data_1' field"))

      assertEventDataAccepted(validator, elg, "data_1", "AAA")
      assertEventDataAccepted(validator, elg, "data_1", "BBB")
      assertEventDataAccepted(validator, elg, "data_1", "CCC")

      assertEventDataNotAccepted(validator, elg, ValidationResultType.REJECTED, "data_1", "DDD")
      assertEventDataAccepted(validator, elg, "data_1", "FIRST")
      assertEventDataNotAccepted(validator, elg, ValidationResultType.REJECTED, "data_1", "SECOND")
    }
  }

  @Test
  fun test_validate_object_list_event_data() {
    val validator = newValidatorByFile("test_object_event_data.json")
    val eventLogGroup = EventLogGroup("object.group", 1)

    val data = hashMapOf<String, Any>("obj" to listOf(hashMapOf("name" to "AAA"), hashMapOf("name" to "NOT_DEFINED")))

    val validatedEventData = validator.guaranteeCorrectEventData(eventLogGroup.id, EventContext.create("test_event", data))
    val objData = validatedEventData["obj"] as List<*>
    TestCase.assertEquals(2, objData.size)
    val elements = objData.map { it as Map<*, *> }
    TestCase.assertEquals("AAA", elements[0]["name"])
    TestCase.assertEquals(ValidationResultType.REJECTED.description, elements[1]["name"])
  }

  @Test
  fun test_validate_nested_objects_event_data() {
    val validator = newValidatorByFile("test_nested_object_event_data.json")
    val eventLogGroup = EventLogGroup("object.group", 1)

    val data = hashMapOf<String, Any>("obj" to hashMapOf("nested_obj" to hashMapOf("name" to "NOT_DEFINED", "count" to "1")))

    val validatedEventData = validator.guaranteeCorrectEventData(eventLogGroup.id, EventContext.create("test_event", data))
    val objData = validatedEventData["obj"] as Map<*, *>
    val nestedObj = objData["nested_obj"] as Map<*, *>
    TestCase.assertEquals(ValidationResultType.REJECTED.description, nestedObj["name"])
    TestCase.assertEquals("1", nestedObj["count"])
  }

  @Test
  fun test_list_validation() {
    val validator = newValidatorByFile("test_list_validation.json")
    val eventLogGroup = EventLogGroup("object.group", 1)

    val data = hashMapOf<String, Any>("elements" to listOf("NOT_DEFINED", "AAA"))

    val validatedEventData = validator.guaranteeCorrectEventData(eventLogGroup.id, EventContext.create("test_event", data))
    val elements = validatedEventData["elements"] as List<*>
    TestCase.assertEquals(2, elements.size)
    TestCase.assertEquals(ValidationResultType.REJECTED.description, elements[0])
    TestCase.assertEquals("AAA", elements[1])
  }

  @Test
  fun test_object_validation_with_custom_rule() {
    doTestWithRuleList("test_object_with_custom_rule.json" ) { validator ->
      val eventLogGroup = EventLogGroup("object.group", 1)

      val data = hashMapOf<String, Any>("obj" to hashMapOf("id_1" to TestCustomActionId.FIRST.name, "id_2" to "NOT_DEFINED"))

      val validatedEventData = validator.guaranteeCorrectEventData(eventLogGroup.id, EventContext.create("test_event", data))
      val objData = validatedEventData["obj"] as Map<*, *>
      TestCase.assertEquals(TestCustomActionId.FIRST.name, objData["id_1"])
      TestCase.assertEquals(ValidationResultType.REJECTED.description, objData["id_2"])
    }
  }

  @Test
  fun test_validate_if_metadata_unreachable() {
    val validator = createUnreachableValidator()
    val eventLogGroup = EventLogGroup("object.group", 1)
    val data: HashMap<String, Any> = hashMapOf( "count" to 1)
    val validatedEventData = validator.guaranteeCorrectEventData(eventLogGroup.id, EventContext.create("test_event", data))

    TestCase.assertEquals(ValidationResultType.UNREACHABLE_METADATA.description, validatedEventData[ValidationResultType.UNREACHABLE_METADATA.description])
  }

  @Test
  fun test_validate_rule_undefined() {
    val validator = newValidatorByFile("test_empty_rule.json")
    val eventLogGroup = EventLogGroup("build.gradle.actions", 1)
    val data: HashMap<String, Any> = hashMapOf("count" to 1)
    val validatedEventData = validator.guaranteeCorrectEventData(eventLogGroup.id, EventContext.create("test_event", data))

    TestCase.assertEquals(ValidationResultType.UNDEFINED_RULE.description, validatedEventData[ValidationResultType.UNDEFINED_RULE.description])
  }

  internal fun createUnreachableValidator(): TestSensitiveDataValidator {
    val storage = object : ValidationRulesPersistedStorage("TEST", TestEventLogMetadataPersistence(""), TestEventLogMetadataLoader("")) {
      override fun isUnreachable(): Boolean {
        return true
      }
    }
    return TestSensitiveDataValidator(storage, "TEST")
  }

  private fun doTestWithRuleList(fileName: String, func: (TestSensitiveDataValidator) -> Unit) {
    val disposable = Disposer.newDisposable()
    try {
      val ep = Extensions.getRootArea().getExtensionPoint(CustomValidationRule.EP_NAME)
      ep.registerExtension(TestExistingValidationRule(), disposable)
      ep.registerExtension(TestThirdPartyValidationRule(), disposable)

      val validator = newValidatorByFile(fileName)
      func(validator)
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  private fun assertEventAccepted(validator: TestSensitiveDataValidator, eventLogGroup: EventLogGroup, eventId: String) {
    val context = EventContext.create(eventId, Collections.emptyMap())
    val actual = validator.validateEvent(context, eventLogGroup.id)
    TestCase.assertEquals("Validation failed for $eventId", ValidationResultType.ACCEPTED, actual)
  }

  private fun assertUndefinedRule(validator: TestSensitiveDataValidator, eventLogGroup: EventLogGroup, s: String) {
    TestCase.assertEquals(ValidationResultType.UNDEFINED_RULE, validator.validateEvent(EventContext.create(s, Collections.emptyMap()),
                                                                                       eventLogGroup.id))
  }

  private fun assertIncorrectRule(validator: TestSensitiveDataValidator, eventLogGroup: EventLogGroup, s: String) {
    TestCase.assertEquals(ValidationResultType.INCORRECT_RULE, validator.validateEvent(EventContext.create(s, Collections.emptyMap()),
                                                                                       eventLogGroup.id))
  }

  private fun assertThirdPartyRule(validator: TestSensitiveDataValidator, eventLogGroup: EventLogGroup, s: String) {
    TestCase.assertEquals(ValidationResultType.THIRD_PARTY, validator.validateEvent(EventContext.create(s, Collections.emptyMap()),
                                                                                    eventLogGroup.id))
  }

  private fun assertEventRejected(validator: TestSensitiveDataValidator, eventLogGroup: EventLogGroup, s: String) {
    TestCase.assertEquals(ValidationResultType.REJECTED, validator.validateEvent(EventContext.create(s, Collections.emptyMap()), eventLogGroup.id))
  }

  private fun assertEventDataAccepted(validator: TestSensitiveDataValidator, eventLogGroup: EventLogGroup, key: String, dataValue: String) {
    val data = FeatureUsageData().addData(key, dataValue).build()
    val (preparedKey, preparedValue) = data.entries.iterator().next()
    val validatedEventData = validator.guaranteeCorrectEventData(eventLogGroup.id, EventContext.create("test_event", data))
    TestCase.assertEquals(preparedValue, validatedEventData[preparedKey])
  }

  private fun assertEventDataNotAccepted(validator: TestSensitiveDataValidator,
                                         eventLogGroup: EventLogGroup,
                                         resultType: ValidationResultType,
                                         key: String,
                                         dataValue: String) {
    val eventContext = EventContext.create("test_event", FeatureUsageData().addData(key, dataValue).build())
    val validatedEventData = validator.guaranteeCorrectEventData(eventLogGroup.id, eventContext)
    TestCase.assertEquals(resultType.description, validatedEventData[key])
  }

  @Suppress("unused")
  internal enum class TestCustomActionId {FIRST, SECOND, THIRD}

  internal inner class TestLocalEnumCustomValidationRule : LocalEnumCustomValidationRule("custom_action_id", TestCustomActionId::class.java)

  internal inner class TestExistingValidationRule : LocalEnumCustomValidationRule("existing_rule", TestCustomActionId::class.java)

  internal inner class TestThirdPartyValidationRule : CustomValidationRule() {
    override fun acceptRuleId(ruleId: String?): Boolean = "third_party_rule" == ruleId

    override fun doValidate(data: String, context: EventContext): ValidationResultType {
      return if (data == "FIRST") ValidationResultType.ACCEPTED else ValidationResultType.THIRD_PARTY
    }
  }
}