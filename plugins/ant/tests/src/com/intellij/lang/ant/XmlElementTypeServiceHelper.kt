package com.intellij.lang.ant

import com.intellij.lang.html.BackendHtmlElementFactory
import com.intellij.lang.html.BasicHtmlElementFactory
import com.intellij.lang.xml.BackendXmlElementFactory
import com.intellij.lang.xml.BasicXmlElementFactory
import com.intellij.mock.MockApplication
import com.intellij.openapi.Disposable

/**
 * Copy of [com.intellij.xml.XmlElementTypeServiceHelper]
 * Copied to avoid "cyclic module dependency" exception
 */
internal object XmlElementTypeServiceHelper {
  @JvmStatic
  fun registerXmlElementTypeServices(
    application: MockApplication,
    testRootDisposable: Disposable,
  ) {
    application.registerService(
      BasicXmlElementFactory::class.java,
      BackendXmlElementFactory(),
      testRootDisposable,
    )

    application.registerService(
      BasicHtmlElementFactory::class.java,
      BackendHtmlElementFactory(),
      testRootDisposable,
    )
  }
}