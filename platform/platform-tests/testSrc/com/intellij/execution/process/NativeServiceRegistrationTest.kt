package com.intellij.execution.process

import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.IoService
import com.intellij.util.net.ssl.OsCertificatesService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class NativeServiceRegistrationTest {
  @Test
  fun `the process service is registered`() {
    assertThat(LocalProcessService.getInstance().javaClass.name).isEqualTo("com.intellij.execution.process.LocalProcessServiceImpl")
  }

  @Test
  fun `the proxy service is registered`() {
    assertThat(IoService.getInstance().javaClass.name).isEqualTo("com.intellij.util.io.IoServiceImpl")
  }

  @Test
  @SystemProperty(propertyKey = "ide.load.os.certificates", propertyValue = "false")
  fun `the certificate service honors disabled loading`() {
    val service = OsCertificatesService.getInstance()
    assertThat(service.javaClass.name).isEqualTo("com.intellij.util.net.ssl.OsCertificatesServiceImpl")
    assertThat(service.getCustomOsSpecificTrustedCertificates()).isEmpty()
  }
}
