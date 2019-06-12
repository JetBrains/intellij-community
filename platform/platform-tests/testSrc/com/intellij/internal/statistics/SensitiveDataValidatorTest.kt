// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.validator.SensitiveDataValidator
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.FUSRule
import com.intellij.internal.statistic.eventLog.validator.rules.impl.LocalEnumCustomWhitelistRule
import com.intellij.internal.statistic.eventLog.validator.rules.impl.RegexpWhiteListRule
import com.intellij.internal.statistic.eventLog.validator.rules.utils.WhiteListSimpleRuleFactory.parseSimpleExpression
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import junit.framework.TestCase
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.util.*
import java.util.regex.Pattern
import kotlin.test.assertTrue

class SensitiveDataValidatorTest : UsefulTestCase() {
  private var myFixture: CodeInsightTestFixture? = null

  override fun setUp() {
    super.setUp()

    val factory = IdeaTestFixtureFactory.getFixtureFactory()
    val fixtureBuilder = factory.createFixtureBuilder("SensitiveDataValidatorTest")
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixtureBuilder.fixture)
    myFixture?.setUp()
  }

  override fun tearDown() {
    super.tearDown()
    try {
      myFixture?.tearDown()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      myFixture = null
    }
  }

  @Test
  fun test_refexg_escapes() {
    var foo = "[aa] \\ \\p{Lower} (a|b|c) [a-zA-Z_0-9] X?+ X*+ X?? [\\p{L}&&[^\\p{Lu}]] "
    val pattern = Pattern.compile(RegexpWhiteListRule.escapeText(foo))
    assertTrue(pattern.matcher(foo).matches())
    assert(true)
  }

  @Test
  fun test_parse_simple_expression() {
    assertOrderedEquals(parseSimpleExpression("aa"), "aa")
    assertOrderedEquals(parseSimpleExpression("aa{bb}cc"), "aa", "{bb}", "cc")
    assertOrderedEquals(parseSimpleExpression("{bb}{cc}"), "{bb}", "{cc}")
    assertOrderedEquals(parseSimpleExpression("a{bb}v{cc}d"), "a", "{bb}", "v", "{cc}", "d")
    assertOrderedEquals(parseSimpleExpression("ccc}ddd"), "ccc}ddd")

    // incorrect
    assertSize(0, parseSimpleExpression(""))
    assertSize(0, parseSimpleExpression("{aaaa"))
    assertSize(0, parseSimpleExpression("{bb}{cc"))
    assertSize(0, parseSimpleExpression("{bb{vv}vv}"))
    assertSize(0, parseSimpleExpression("{{v}"))
  }

  @Test
  fun test_empty_rule() {
    val validator = createTestSensitiveDataValidator(loadContent("test_empty_rule.json"))
    val eventLogGroup = EventLogGroup("build.gradle.actions", 1)

    assertEmpty(validator.getEventRules(eventLogGroup))
    assertTrue(validator.getEventDataRules(eventLogGroup).isEmpty())

    assertUndefinedRule(validator, eventLogGroup, "<any-string-accepted>")
    assertEventDataUndefinedRule(validator, eventLogGroup, "<any-key-accepted>", "<any-string-accepted>")
  }

  @Test
  fun test_simple_enum_rules() {
    val validator = createTestSensitiveDataValidator(loadContent("test_simple_enum_rules.json"))
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
  fun test_simple_regexp_rules() {
    // custom regexp is:   (.+)\s*:\s*(.*)  => matches  'aaa/java.lang.String'
    val validator = createTestSensitiveDataValidator(loadContent("test_simple_regexp_rules.json"))

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
  }

  @Test
  fun test_simple_expression_rules() {
    // custom expression is:   "JUST_TEXT[_{regexp:\\d+(\\+)?}_]_xxx_{enum:AAA|BBB|CCC}_zzz{enum#myEnum}_yyy"
    val validator = createTestSensitiveDataValidator(loadContent("test_simple_expression_rules.json"))
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
//  @Test
//  fun test_simple_util_rules() {
//    val validator = createTestSensitiveDataValidator(loadContent("test_simple_util_rules.json"))
//    val elg = EventLogGroup("diagram.usages.trigger", 1)
//
//    assertSize(1, validator.getEventRules(elg))
//
//    assertEventAccepted(validator, elg, "show.diagram->JAVA")
//    assertEventAccepted(validator, elg, "show.diagram->MAVEN")
//    assertEventAccepted(validator, elg, "show.diagram->third.party")
//    assertEventRejected(validator, elg, "show.diagram->foo")
//  }

  @Test
  fun test_regexp_rule_with_global_regexps() {
    val validator = createTestSensitiveDataValidator(loadContent("test_regexp_rule-with-global-regexp.json"))
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
    val validator = createTestSensitiveDataValidator(loadContent("test_validate_event_data.json"))
    val elg = EventLogGroup("system.keys.group", 1)

    val platformDataKeys: MutableList<String> = Arrays.asList("plugin", "project", "os", "plugin_type",
                                                              "lang", "current_file", "input_event", "place")
    for (platformDataKey in platformDataKeys) {
      assertEventDataAccepted(validator, elg, platformDataKey, "<validated>")
    }
    assertEventDataAccepted(validator, elg, "ed_1", "AA")
    assertEventDataAccepted(validator, elg, "ed_2", "REF_BB")
    assertEventDataRejected(validator, elg, "ed_1", "CC")
    assertEventDataRejected(validator, elg, "ed_2", "REF_XX")
    assertEventDataRuleUndefined(validator, elg, "undefined", "<unknown>")
  }

  @Test
  fun test_validate_custom_rule_with_local_enum() {
    val rule = TestLocalEnumCustomWhitelistRule()

    Assert.assertEquals(ValidationResultType.ACCEPTED, rule.validate("FIRST", EventContext.create("FIRST", emptyMap())))
    Assert.assertEquals(ValidationResultType.ACCEPTED, rule.validate("SECOND", EventContext.create("FIRST", emptyMap())))
    Assert.assertEquals(ValidationResultType.ACCEPTED, rule.validate("THIRD", EventContext.create("FIRST", emptyMap())))

    Assert.assertEquals(ValidationResultType.REJECTED, rule.validate("FORTH", EventContext.create("FIRST", emptyMap())))
    Assert.assertEquals(ValidationResultType.REJECTED, rule.validate("", EventContext.create("FIRST", emptyMap())))
    Assert.assertEquals(ValidationResultType.REJECTED, rule.validate("UNKNOWN", EventContext.create("FIRST", emptyMap())))
  }


  private fun assertEventAccepted(validator: SensitiveDataValidator, eventLogGroup: EventLogGroup, s: String) {
    TestCase.assertEquals(ValidationResultType.ACCEPTED, validator.validateEvent(eventLogGroup, EventContext.create(s, Collections.emptyMap())))
  }

  private fun assertUndefinedRule(validator: SensitiveDataValidator, eventLogGroup: EventLogGroup, s: String) {
    TestCase.assertEquals(ValidationResultType.UNDEFINED_RULE, validator.validateEvent(eventLogGroup, EventContext.create(s, Collections.emptyMap())))
  }

  private fun assertEventRejected(validator: SensitiveDataValidator, eventLogGroup: EventLogGroup, s: String) {
    TestCase.assertEquals(ValidationResultType.REJECTED, validator.validateEvent(eventLogGroup, EventContext.create(s, Collections.emptyMap())))
  }

  private fun assertEventDataAccepted(validator: TestSensitiveDataValidator, eventLogGroup: EventLogGroup, key: String, dataValue: String) {
    TestCase.assertEquals(ValidationResultType.ACCEPTED, validator.validateEventData(eventLogGroup, key, dataValue))
  }

  private fun assertEventDataUndefinedRule(validator: TestSensitiveDataValidator, eventLogGroup: EventLogGroup, key: String, dataValue: String) {
    TestCase.assertEquals(ValidationResultType.UNDEFINED_RULE, validator.validateEventData(eventLogGroup, key, dataValue))
  }
  private fun assertEventDataRejected(validator: TestSensitiveDataValidator, eventLogGroup: EventLogGroup, key: String, dataValue: String) {
    TestCase.assertEquals(ValidationResultType.REJECTED, validator.validateEventData(eventLogGroup, key, dataValue))
  }

  private fun assertEventDataRuleUndefined(validator: TestSensitiveDataValidator, eventLogGroup: EventLogGroup, key: String, dataValue: String) {
    TestCase.assertEquals(ValidationResultType.UNDEFINED_RULE, validator.validateEventData(eventLogGroup, key, dataValue))
  }

  private fun createTestSensitiveDataValidator(content: String): TestSensitiveDataValidator {
    return TestSensitiveDataValidator(content).update() as TestSensitiveDataValidator
  }


  private fun loadContent(fileName: String): String {
    val file = File(PlatformTestUtil.getPlatformTestDataPath() + "fus/validation/" + fileName)
    assertTrue { file.exists() }
    return FileUtil.loadFile(file)
  }


  internal inner class TestSensitiveDataValidator constructor(private val myContent: String) : SensitiveDataValidator("TEST") {
    override fun getWhiteListContent(): String {
      return myContent
    }

    fun getEventRules(group: EventLogGroup): Array<FUSRule> {
      val whiteListRule = eventsValidators[group.id]

      return if (whiteListRule == null) FUSRule.EMPTY_ARRAY else whiteListRule.eventIdRules
    }

    fun getEventDataRules(group: EventLogGroup): Map<String, Array<FUSRule>> {
      val whiteListRule = eventsValidators[group.id]

      return if (whiteListRule == null) emptyMap() else whiteListRule.eventDataRules
    }

    fun validateEventData(group: EventLogGroup, key: String, value: Any): ValidationResultType {
      if (FeatureUsageData.platformDataKeys.contains(key)) return ValidationResultType.ACCEPTED

      val whiteListRule = eventsValidators[group.id]
      return if (whiteListRule == null || !whiteListRule.areEventDataRulesDefined()) ValidationResultType.UNDEFINED_RULE
      else whiteListRule.validateEventData(key, value, EventContext.create("", Collections.emptyMap())) // there are no configured rules
    }
  }

  internal enum class TestCustomActionId {FIRST, SECOND, THIRD}

  internal inner class TestLocalEnumCustomWhitelistRule : LocalEnumCustomWhitelistRule("custom_action_id", TestCustomActionId::class.java)
}