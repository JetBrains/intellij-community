package org.jetbrains.plugins.groovy.intentions

/**
 * @author Niels Harremoes
 */
class InvertIfTest extends GrIntentionTestCase {

  String intentionName = GroovyIntentionsBundle.message("invert.if.intention.name")

  public void testDoNotTriggerOnIncompleteIf() throws Exception {
    doAntiTest '''
i<caret>f () {
  succes
} else {
  no_succes
}
''', intentionName

  }

  private void doTest(String before, String after) {

    doTextTest before, intentionName, after
  }

  public void testSimpleCondition() throws Exception {

    doTest '''
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

  public void testCallCondition() throws Exception {

    doTest '''
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

  public void testComplexCondition() throws Exception {
    doTest '''
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

  public void testNegatedComplexCondition() throws Exception {
    doTest '''
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

  public void testNegatedSimpleCondition() throws Exception {
    doTest '''
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

  public void testNoElseBlock() throws Exception {
    doTest '''
i<caret>f (a) {
    succes
}
''', '''i<caret>f (!a) {
} else {
    succes
}
'''
  }

}
