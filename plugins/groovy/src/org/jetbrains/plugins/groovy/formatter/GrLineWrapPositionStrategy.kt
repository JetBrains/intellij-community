// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.formatter

import com.intellij.openapi.editor.GenericLineWrapPositionStrategy

class GrLineWrapPositionStrategy : GenericLineWrapPositionStrategy() {

  init {
    // Commas.
    addRule(Rule(',', WrapCondition.AFTER))

    // Symbols to wrap either before or after.
    addRule(Rule(' '))
    addRule(Rule('\t'))

    // Symbols to wrap after.
    addRule(Rule(';', WrapCondition.AFTER))
    addRule(Rule(')', WrapCondition.AFTER))

    // Symbols to wrap before
    addRule(Rule('(', WrapCondition.BEFORE))
    addRule(Rule('.', WrapCondition.AFTER))
  }
}