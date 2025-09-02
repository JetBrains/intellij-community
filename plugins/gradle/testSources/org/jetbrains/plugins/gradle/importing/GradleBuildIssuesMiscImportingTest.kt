// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.function.Consumer

class GradleBuildIssuesMiscImportingTest : BuildViewMessagesImportingTestCase() {

  var lastImportErrorMessage: String? = null

  override fun handleImportFailure(errorMessage: String, errorDetails: String?) {
    // do not fail tests with failed builds and save the import error message
    lastImportErrorMessage = errorMessage
  }

  @Test
  fun `test out of memory build failures`() {
    createProjectSubFile("gradle.properties",
                         "org.gradle.jvmargs=-Xmx100m")
    importProject("""
      List list = new ArrayList()
      while (true) {
         list.add(new byte[1024 * 1024])
      }
      """.trimIndent())

    val buildScript = myProjectConfig.toNioPath().toString()

    val oomMessage = if (lastImportErrorMessage!!.contains("Java heap space")) "Java heap space" else "GC overhead limit exceeded"
    assertSyncViewTreeEquals { treeTestPresentation ->
      assertThat(treeTestPresentation).satisfiesAnyOf(
        Consumer {
          assertThat(it).isEqualTo("-\n" +
                                   " -failed\n" +
                                   "  -build.gradle\n" +
                                   "   $oomMessage")

        },
        Consumer {
          assertThat(it).isEqualTo("-\n" +
                                   " -failed\n" +
                                   "  $oomMessage")

        }
      )
    }

    assertSyncViewSelectedNode(oomMessage) { text ->
      assertThat(text).satisfiesAnyOf(
        Consumer {
          assertThat(it).startsWith("""
            * Where:
            Build file '$buildScript' line: 10
      
            * What went wrong:
            Out of memory. $oomMessage
      
            Possible solution:
             - Check the JVM memory arguments defined for the gradle process in:
               gradle.properties in project root directory
          """.trimIndent())
        },
        Consumer {
          assertThat(it).startsWith("""
            * What went wrong:
            Out of memory. $oomMessage
      
            Possible solution:
             - Check the JVM memory arguments defined for the gradle process in:
               gradle.properties in project root directory
          """.trimIndent())
        }
      )
    }
  }
}
