package com.intellij.configurationScript

import com.intellij.execution.application.JvmMainMethodRunConfigurationOptions
import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test

class JavaConfigurationFileTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  @Test
   fun `one java`() {
     val result = collectRunConfigurations("""
     runConfigurations:
       java:
         isAlternativeJrePathEnabled: true
     """)
     val options = JvmMainMethodRunConfigurationOptions()
     options.isAlternativeJrePathEnabled = true
     assertThat(result).containsExactly(options)
   }

   @Test
   fun `one java as list`() {
     val result = collectRunConfigurations("""
     runConfigurations:
       java:
         - isAlternativeJrePathEnabled: true
     """)
     val options = JvmMainMethodRunConfigurationOptions()
     options.isAlternativeJrePathEnabled = true
     assertThat(result).containsExactly(options)
   }

   @Test
   fun `one v as list - template`() {
     val result = collectRunConfigurations("""
     runConfigurations:
       templates:
         java:
           - isAlternativeJrePathEnabled: true
     """, isTemplatesOnly = true)
     val options = JvmMainMethodRunConfigurationOptions()
     options.isAlternativeJrePathEnabled = true
     assertThat(result).containsExactly(options)
   }
}