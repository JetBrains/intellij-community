package com.intellij.grazie.cloud

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Covers the choice of a connected connector and the connector that reads a published endpoint. */
@Timeout(30)
class GrazieLlmEndpointCloudConnectorTest {
  private var published: GrazieLlmEndpoint? = null

  private val connector = GrazieLlmEndpointCloudConnector { published }

  @Test
  fun `no published endpoint leaves the connector disconnected`() {
    assertThat(connector.seemsCloudConnected()).isFalse()
    assertThat(connector.api()).isNull()
  }

  @Test
  fun `a published endpoint connects the connector`() {
    published = ENDPOINT

    assertThat(connector.seemsCloudConnected()).isTrue()
    assertThat(connector.api()).isNotNull()
  }

  @Test
  fun `the base URL keeps the endpoint and ends with one slash`() {
    assertThat(baseUrl("http://127.0.0.1:1234/jcp.ide.grazie.ai_gateway")).isEqualTo("http://127.0.0.1:1234/jcp.ide.grazie.ai_gateway/")
    assertThat(baseUrl("https://api.example.com:8443/route/")).isEqualTo("https://api.example.com:8443/route/")
  }

  @Test
  fun `the choice prefers the first connected connector`() {
    val disconnected = FakeConnector(connected = false)
    val connected = FakeConnector(connected = true)

    assertThat(GrazieCloudConnector.choose(listOf(disconnected, connected))).isSameAs(connected)
    assertThat(GrazieCloudConnector.choose(listOf(disconnected))).isSameAs(disconnected)
    assertThat(GrazieCloudConnector.choose(emptyList())).isNull()
  }

  @Test
  fun `the endpoint prints no header value`() {
    assertThat(ENDPOINT.toString()).doesNotContain("proxy-secret")
  }

  private class FakeConnector(private val connected: Boolean) : GrazieCloudConnector {
    override fun seemsCloudConnected(): Boolean = connected

    override fun api(): GrazieCloudApi? = null

    override fun hasQuota(): Boolean = false
  }

  private companion object {
    private val ENDPOINT = GrazieLlmEndpoint(
      url = "http://127.0.0.1:1234/jcp.ide.grazie.ai_gateway",
      headers = mapOf("x-air-proxy-key" to "proxy-secret"),
    )
  }

  private fun baseUrl(endpointUrl: String): String {
    published = GrazieLlmEndpoint(endpointUrl, emptyMap())
    return connector.api()!!.baseApiUrl
  }
}
