// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.events.scheme


object EventSchemeValidator {
  private const val SYMBOLS_TO_REPLACE_FIELD_NAME = ":;, "

  @JvmStatic
  fun validateEventScheme(eventsScheme: List<GroupDescriptor>): List<String> {
    val groupNames = HashSet<String>()
    val errors = ArrayList<String>()
    for (group in eventsScheme.toSet()) {
      errors.addAll(validateGroupScheme(group, groupNames))
    }
    return errors
  }

  private fun validateGroupScheme(group: GroupDescriptor,
                                  validatedGroupNames: MutableSet<String>): List<String> {
    val errors = ArrayList<String>()
    val groupId = group.id
    if (groupId.isBlank()) {
      errors.add("Group id is null or empty")
    }
    if (!validatedGroupNames.add(groupId)) {
      errors.add("Duplicate group `${groupId}`")
    }
    if (group.version <= 0) {
      errors.add("Group version should be not null and > 0 (groupId=${groupId})")
    }
    errors.addAll(validateEvents(group.schema, groupId))
    return errors
  }

  private fun validateEvents(schema: Set<EventDescriptor>,
                             groupId: String): List<String> {
    val errors = ArrayList<String>()
    if (schema.isEmpty()) {
      errors.add("Group should contains at least one event (groupId=${groupId})")
      return errors
    }
    val eventsNames = HashSet<String>()
    for (event in schema) {
      val eventId = event.event
      if (eventId.isBlank()) {
        errors.add("Event id is null or empty (groupId=${groupId})")
      }
      else if (!eventsNames.add(eventId)) {
        errors.add("Duplicate event (groupId=${groupId}, eventId=${eventId})")
      }
      if (containsSystemSymbols(eventId, null)) {
        errors.add(
          "Only printable ASCII symbols except '\" are allowed in event name " +
          "(groupId=${groupId}, eventId=${eventId})"
        )
      }
      errors.addAll(validateFields(event.fields, groupId, eventId))
    }
    return errors
  }

  private fun validateFields(fields: Set<FieldDescriptor>,
                             groupId: String,
                             eventId: String?): List<String> {
    val errors = ArrayList<String>()
    for (field in fields) {
      val fieldName = field.path
      if (fieldName.isBlank()) {
        errors.add("Field path is empty (groupId=${groupId}, eventId=${eventId})")
      }
      if (containsSystemSymbols(fieldName, SYMBOLS_TO_REPLACE_FIELD_NAME)) {
        errors.add(
          "Only printable ASCII symbols except whitespaces and .:;,'\" are allowed in field name " +
          "(groupId=${groupId}, eventId=${eventId}, field=${fieldName})"
        )
      }
      errors.addAll(validateRules(groupId, eventId, fieldName, field.value))
    }
    return errors
  }

  private fun validateRules(groupId: String,
                            eventId: String?,
                            fieldName: String?,
                            rules: Set<String>?): List<String> {
    val errors = ArrayList<String>()
    if (rules == null) {
      errors.add("Validation rules are not specified (groupId=${groupId}, eventId=${eventId}, field=${fieldName})")
      return errors
    }
    for (validationRule in rules) {
      if (validationRule.isBlank()) {
        errors.add("Validation rule is null or empty (groupId=${groupId}, eventId=${eventId}, field=${fieldName})")
      }
      if (validationRule == "{regexp:.*}" || validationRule == "{regexp:.+}") {
        errors.add(
          "Regexp should be more strict to prevent accidentally reporting sensitive data (groupId=${groupId}, eventId=${eventId}, field=${fieldName})")
      }
      val rule = unwrapRule(validationRule)
      if (rule.startsWith("enum:")) {
        if (containsSystemSymbols(validationRule, null)) {
          errors.add(
            "Only printable ASCII symbols except '\" are allowed in validation rule " +
            "(groupId=${groupId}, eventId=${eventId}, field=${fieldName})"
          )
        }
      }
    }
    return errors
  }

  private fun unwrapRule(rule: String): String {
    val trimmedRule = rule.trim()
    return if (trimmedRule.startsWith("{") && trimmedRule.endsWith("}")) {
      trimmedRule.substring(1, trimmedRule.length - 1)
    }
    else {
      rule
    }
  }


  private fun containsSystemSymbols(value: String?, toReplace: String?): Boolean {
    if (value == null) return false
    for (element in value) {
      if (!isAscii(element)) return true
      if (isWhiteSpaceToReplace(element)) return true
      if (isSymbolToReplace(element, toReplace)) return true
      if (isProhibitedSymbol(element)) return true
    }
    return false
  }

  private fun isAscii(c: Char): Boolean {
    return c.code <= 127
  }

  private fun isSymbolToReplace(c: Char, toReplace: String?): Boolean {
    return if (toReplace != null && containsChar(toReplace, c)) {
      true
    }
    else isAsciiControl(c)
  }

  private fun isWhiteSpaceToReplace(c: Char): Boolean {
    return c == '\n' || c == '\r' || c == '\t'
  }

  private fun isAsciiControl(c: Char): Boolean {
    return c.code < 32 || c.code == 127
  }

  private fun isProhibitedSymbol(c: Char): Boolean {
    return c == '\'' || c == '"'
  }

  private fun containsChar(str: String, c: Char): Boolean {
    for (element in str) {
      if (element == c) return true
    }
    return false
  }
}