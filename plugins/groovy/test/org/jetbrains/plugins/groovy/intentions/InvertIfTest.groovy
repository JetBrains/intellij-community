package org.jetbrains.plugins.groovy.intentions

/**
 * @author Niels Harremoes
 */
class InvertIfTest extends GrIntentionTestCase {

  InvertIfTest() {
    super(GroovyIntentionsBundle.message("invert.if.intention.name"))
  }

  public void testDoNotTriggerOnIncompleteIf() {
    doAntiTest '''
i<caret>f () {
  succes
} else {
  no_succes
}
'''

  }

  public void testSimpleCondition() {
    doTextTest '''
i<caret>f (a) {
    succes
} else {
    no_succes
}
''', '''i<caret>f (!a) {
    no_succes
} else {
    succes
}
'''
  }

  public void testCallCondition() {

    doTextTest '''
i<caret>f (func()) {
    succes
} else {
    no_succes
}
''', '''i<caret>f (!func()) {
    no_succes
} else {
    succes
}
'''
  }

  public void testComplexCondition() {
    doTextTest '''
i<caret>f (a && b) {
    succes
} else {
    no_succes
}
''', '''i<caret>f (!(a && b)) {
    no_succes
} else {
    succes
}
'''
  }

  public void testNegatedComplexCondition() {
    doTextTest '''
i<caret>f (!(a && b)) {
    succes
} else {
    no_succes
}
''', '''i<caret>f (a && b) {
    no_succes
} else {
    succes
}
'''
  }

  public void testNegatedSimpleCondition() {
    doTextTest '''
i<caret>f (!a) {
    succes
} else {
    no_succes
}
''', '''i<caret>f (a) {
    no_succes
} else {
    succes
}
'''
  }

  public void testNoElseBlock() {
    doTextTest '''
i<caret>f (a) {
    succes
}
nosuccess
''', '''i<caret>f (!a) {
    nosuccess
} else {
    succes
}'''
  }

  public void testEmptyThenBlockIsRemoved() {
    doTextTest '''
i<caret>f (a) {
} else {
    no_succes
}
''', '''i<caret>f (!a) {
    no_succes
}
'''
  }
}