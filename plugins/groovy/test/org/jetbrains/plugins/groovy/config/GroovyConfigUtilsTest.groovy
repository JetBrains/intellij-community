// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.config

import groovy.transform.CompileStatic
import org.junit.Test

import static org.jetbrains.plugins.groovy.config.AbstractConfigUtils.VERSION_GROUP_NAME
import static org.jetbrains.plugins.groovy.config.GroovyConfigUtils.GROOVY_ALL_JAR_PATTERN
import static org.jetbrains.plugins.groovy.config.GroovyConfigUtils.GROOVY_JAR_PATTERN
import static org.junit.Assert.*

@CompileStatic
class GroovyConfigUtilsTest {

  @Test
  void 'groovy jar pattern'() {
    assertTrue "groovy.jar" ==~ GROOVY_JAR_PATTERN
    assertFalse "groovy-.jar" ==~ GROOVY_JAR_PATTERN
    assertTrue "groovy-1.2.3.4.5.jar" ==~ GROOVY_JAR_PATTERN
    assertFalse "groovy-1.2.3.4.5-.jar" ==~ GROOVY_JAR_PATTERN
    assertTrue "groovy-1.2.3.4.5-someString.jar" ==~ GROOVY_JAR_PATTERN
    assertFalse "groovy-1.2.3.4.5-someString-.jar" ==~ GROOVY_JAR_PATTERN
    assertTrue "groovy-1.2.3.4.5-someString-42.jar" ==~ GROOVY_JAR_PATTERN
  }

  @Test
  void 'groovy jar pattern indy'() {
    assertTrue "groovy-indy.jar" ==~ GROOVY_JAR_PATTERN
    assertFalse "groovy--indy.jar" ==~ GROOVY_JAR_PATTERN
    assertTrue "groovy-1.2.3.4.5-indy.jar" ==~ GROOVY_JAR_PATTERN
    assertFalse "groovy-1.2.3.4.5--indy.jar" ==~ GROOVY_JAR_PATTERN
    assertTrue "groovy-1.2.3.4.5-someString-indy.jar" ==~ GROOVY_JAR_PATTERN
    assertFalse "groovy-1.2.3.4.5-someString--indy.jar" ==~ GROOVY_JAR_PATTERN
    assertTrue "groovy-1.2.3.4.5-someString-42-indy.jar" ==~ GROOVY_JAR_PATTERN
  }

  @Test
  void 'groovy jar pattern existing examples'() {
    assertTrue "groovy-1.0.jar" ==~ GROOVY_JAR_PATTERN
    assertTrue "groovy-2.4.15.jar" ==~ GROOVY_JAR_PATTERN
    assertTrue "groovy-2.4.16-SNAPSHOT.jar" ==~ GROOVY_JAR_PATTERN
    assertTrue "groovy-2.5.0-rc-2.jar" ==~ GROOVY_JAR_PATTERN
    assertTrue "groovy-2.6.0-beta-2.jar" ==~ GROOVY_JAR_PATTERN
    assertTrue "groovy-3.0.0-alpha-2.jar" ==~ GROOVY_JAR_PATTERN
  }

  @Test
  void 'groovy-all jar pattern'() {
    assertTrue "groovy-all.jar" ==~ GROOVY_ALL_JAR_PATTERN
    assertFalse "groovy-all-.jar" ==~ GROOVY_ALL_JAR_PATTERN
    assertTrue "groovy-all-1.2.3.4.5.jar" ==~ GROOVY_ALL_JAR_PATTERN
    assertFalse "groovy-all-1.2.3.4.5-.jar" ==~ GROOVY_ALL_JAR_PATTERN
    assertTrue "groovy-all-1.2.3.4.5-someString.jar" ==~ GROOVY_ALL_JAR_PATTERN
    assertFalse "groovy-all-1.2.3.4.5-someString-.jar" ==~ GROOVY_ALL_JAR_PATTERN
    assertTrue "groovy-all-1.2.3.4.5-someString-42.jar" ==~ GROOVY_ALL_JAR_PATTERN
  }

  @Test
  void 'groovy-all jar pattern indy'() {
    assertTrue "groovy-all-indy.jar" ==~ GROOVY_ALL_JAR_PATTERN
    assertFalse "groovy-all--indy.jar" ==~ GROOVY_ALL_JAR_PATTERN
    assertTrue "groovy-all-1.2.3.4.5-indy.jar" ==~ GROOVY_ALL_JAR_PATTERN
    assertFalse "groovy-all-1.2.3.4.5--indy.jar" ==~ GROOVY_ALL_JAR_PATTERN
    assertTrue "groovy-all-1.2.3.4.5-someString-indy.jar" ==~ GROOVY_ALL_JAR_PATTERN
    assertFalse "groovy-all-1.2.3.4.5-someString--indy.jar" ==~ GROOVY_ALL_JAR_PATTERN
    assertTrue "groovy-all-1.2.3.4.5-someString-42-indy.jar" ==~ GROOVY_ALL_JAR_PATTERN
  }

  @Test
  void 'groovy-all jar pattern minimal'() {
    assertTrue "groovy-all-minimal.jar" ==~ GROOVY_ALL_JAR_PATTERN
    assertFalse "groovy-all-minimal-.jar" ==~ GROOVY_ALL_JAR_PATTERN
    assertTrue "groovy-all-minimal-1.2.3.4.5.jar" ==~ GROOVY_ALL_JAR_PATTERN
    assertFalse "groovy-all-minimal-1.2.3.4.5-.jar" ==~ GROOVY_ALL_JAR_PATTERN
    assertTrue "groovy-all-minimal-1.2.3.4.5-someString.jar" ==~ GROOVY_ALL_JAR_PATTERN
    assertFalse "groovy-all-minimal-1.2.3.4.5-someString-.jar" ==~ GROOVY_ALL_JAR_PATTERN
    assertTrue "groovy-all-minimal-1.2.3.4.5-someString-42.jar" ==~ GROOVY_ALL_JAR_PATTERN
  }

  @Test
  void 'groovy-all jar pattern minimal indy'() {
    assertTrue "groovy-all-minimal-indy.jar" ==~ GROOVY_ALL_JAR_PATTERN
    assertFalse "groovy-all-minimal--indy.jar" ==~ GROOVY_ALL_JAR_PATTERN
    assertTrue "groovy-all-minimal-1.2.3.4.5-indy.jar" ==~ GROOVY_ALL_JAR_PATTERN
    assertFalse "groovy-all-minimal-1.2.3.4.5--indy.jar" ==~ GROOVY_ALL_JAR_PATTERN
    assertTrue "groovy-all-minimal-1.2.3.4.5-someString-indy.jar" ==~ GROOVY_ALL_JAR_PATTERN
    assertFalse "groovy-all-minimal-1.2.3.4.5-someString--indy.jar" ==~ GROOVY_ALL_JAR_PATTERN
    assertTrue "groovy-all-minimal-1.2.3.4.5-someString-42-indy.jar" ==~ GROOVY_ALL_JAR_PATTERN
  }

  @Test
  void 'groovy-all jar pattern existing examples'() {
    testGroovyAllVersion "groovy-all-1.0.jar", "1.0"
    testGroovyAllVersion "groovy-all-2.4.15.jar", "2.4.15"
    testGroovyAllVersion "groovy-all-2.4.16-SNAPSHOT.jar", "2.4.16-SNAPSHOT"
    testGroovyAllVersion "groovy-all-2.5.0-rc-2.jar", "2.5.0-rc-2"
    testGroovyAllVersion "groovy-all-2.6.0-beta-2.jar", "2.6.0-beta-2"
    testGroovyAllVersion "groovy-all-3.0.0-alpha-2.jar", "3.0.0-alpha-2"

    testGroovyAllVersion "groovy-all-1.0-indy.jar", "1.0"
    testGroovyAllVersion "groovy-all-2.4.15-indy.jar", "2.4.15"
    testGroovyAllVersion "groovy-all-2.4.16-SNAPSHOT-indy.jar", "2.4.16-SNAPSHOT"
    testGroovyAllVersion "groovy-all-2.5.0-rc-2-indy.jar", "2.5.0-rc-2"
    testGroovyAllVersion "groovy-all-2.6.0-beta-2-indy.jar", "2.6.0-beta-2"
    testGroovyAllVersion "groovy-all-3.0.0-alpha-2-indy.jar", "3.0.0-alpha-2"

    testGroovyAllVersion "groovy-all-minimal-1.0.jar", "1.0"
    testGroovyAllVersion "groovy-all-minimal-2.4.15.jar", "2.4.15"
    testGroovyAllVersion "groovy-all-minimal-2.4.16-SNAPSHOT.jar", "2.4.16-SNAPSHOT"
    testGroovyAllVersion "groovy-all-minimal-2.5.0-rc-2.jar", "2.5.0-rc-2"
    testGroovyAllVersion "groovy-all-minimal-2.6.0-beta-2.jar", "2.6.0-beta-2"
    testGroovyAllVersion "groovy-all-minimal-3.0.0-alpha-2.jar", "3.0.0-alpha-2"

    testGroovyAllVersion "groovy-all-minimal-1.0-indy.jar", "1.0"
    testGroovyAllVersion "groovy-all-minimal-2.4.15-indy.jar", "2.4.15"
    testGroovyAllVersion "groovy-all-minimal-2.4.16-SNAPSHOT-indy.jar", "2.4.16-SNAPSHOT"
    testGroovyAllVersion "groovy-all-minimal-2.5.0-rc-2-indy.jar", "2.5.0-rc-2"
    testGroovyAllVersion "groovy-all-minimal-2.6.0-beta-2-indy.jar", "2.6.0-beta-2"
    testGroovyAllVersion "groovy-all-minimal-3.0.0-alpha-2-indy.jar", "3.0.0-alpha-2"
  }

  private static void testGroovyAllVersion(String jarName, String expectedVersion) {
    def matcher = jarName =~ GROOVY_ALL_JAR_PATTERN
    assertTrue matcher.matches()
    def version = matcher.group VERSION_GROUP_NAME
    assertEquals expectedVersion, version
  }
}
