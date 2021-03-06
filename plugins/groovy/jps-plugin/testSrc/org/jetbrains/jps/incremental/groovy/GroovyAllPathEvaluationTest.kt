// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.groovy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroovyAllPathEvaluationTest {
  @Test
  fun `single groovy-all JAR`() {
    assertEquals("/lib/groovy-all-2.4.17.jar",
                 evalPathForParentClassloader("/lib/foo.jar", "/lib/groovy-all-2.4.17.jar"))
  }

  @Test
  fun `groovy-all and groovy-eclipse`() {
    assertEquals("/lib/groovy-all-2.4.17.jar",
                 evalPathForParentClassloader("/lib/groovy-eclipse-batch-2.0.jar", "/lib/groovy-all-2.4.17.jar"))
  }

  @Test
  fun `two groovy-all JARs`() {
    assertNull(evalPathForParentClassloader("/lib/groovy-all-2.4.17.jar",
                                            "/lib/groovy-all-2.4.18.jar"))

  }

  @Test
  fun `many groovy JARs`() {
    assertNull(evalPathForParentClassloader("/lib/groovy-2.4.12.jar",
                                            "/lib/groovy-ant-2.4.12.jar",
                                            "/lib/groovy-bsf-2.4.12.jar",
                                            "/lib/groovy-console-2.4.12.jar"))
  }

  @Test
  fun `groovy-all and JPS plugin JARs`() {
    assertEquals("/lib/groovy-all-2.4.17.jar",
                 evalPathForParentClassloader("/lib/groovy-jps-193.239.jar",
                                              "/lib/groovy-rt-193.239.jar",
                                              "/lib/groovy-constants-rt-193.239.jar",
                                              "/lib/groovy-all-2.4.17.jar"))
    assertEquals("/lib/groovy-all-2.4.17.jar",
                 evalPathForParentClassloader("/lib/groovy-all-2.4.17.jar",
                                              "/lib/groovy-jps.jar",
                                              "/lib/groovy-constants-rt.jar",
                                              "/lib/groovy-rt.jar"))
  }

  @Test
  fun `groovy jar is OK since 2_5 because it does not need jars from other artifacts to compile`() {
    assertEquals("/lib/groovy-2.5.11.jar", evalPathForParentClassloader(
      "/lib/groovy-2.5.11.jar",
      "/lib/groovy-test-2.5.11.jar"
    ))
  }

  private fun evalPathForParentClassloader(vararg classpath: String) =
    InProcessGroovyc.evaluatePathToGroovyJarForParentClassloader(classpath.toList())
}