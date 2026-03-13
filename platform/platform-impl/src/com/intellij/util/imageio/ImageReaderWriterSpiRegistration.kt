// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.imageio

import com.intellij.diagnostic.PluginException
import com.intellij.ide.ApplicationLoadListener
import com.intellij.openapi.application.Application
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import java.nio.file.Path
import java.util.Locale
import javax.imageio.spi.IIORegistry
import javax.imageio.spi.IIOServiceProvider
import javax.imageio.spi.ImageReaderSpi
import javax.imageio.spi.ImageWriterSpi

private const val READ_SPI_TYPE = "read"
private const val WRITE_SPI_TYPE = "write"

private val EP_NAME = ExtensionPointName<ImageReaderWriterSpiBean>("com.intellij.imageReaderWriterSpi")

internal class ImageReaderWriterSpiRegistrar : ApplicationLoadListener {
  override suspend fun beforeApplicationLoaded(application: Application, configPath: Path) {
    val registry = IIORegistry.getDefaultInstance()
    EP_NAME.extensionList.forEach { it.registerIn(registry) }
    EP_NAME.addExtensionPointListener(application, object : ExtensionPointListener<ImageReaderWriterSpiBean> {
      override fun extensionAdded(extension: ImageReaderWriterSpiBean, pluginDescriptor: PluginDescriptor) {
        extension.registerIn(registry)
      }

      override fun extensionRemoved(extension: ImageReaderWriterSpiBean, pluginDescriptor: PluginDescriptor) {
        extension.deregisterIn(registry)
      }
    })
  }
}

internal class ImageReaderWriterSpiBean : BaseKeyedLazyInstance<IIOServiceProvider>() {
  @Attribute("type")
  @RequiredElement
  lateinit var type: String

  @Attribute("implementationClass")
  @RequiredElement
  lateinit var implementationClass: String

  override fun getImplementationClassName(): String = implementationClass

  fun registerIn(registry: IIORegistry) {
    when (type.lowercase(Locale.ROOT)) {
      READ_SPI_TYPE -> {
        registry.registerServiceProvider(asReaderSpi(), ImageReaderSpi::class.java)
      }
      WRITE_SPI_TYPE -> {
        registry.registerServiceProvider(asWriterSpi(), ImageWriterSpi::class.java)
      }
      else -> {
        throw unknownSpiType()
      }
    }
  }

  fun deregisterIn(registry: IIORegistry) {
    when (type.lowercase(Locale.ROOT)) {
      READ_SPI_TYPE -> {
        registry.deregisterServiceProvider(asReaderSpi(), ImageReaderSpi::class.java)
      }
      WRITE_SPI_TYPE -> {
        registry.deregisterServiceProvider(asWriterSpi(), ImageWriterSpi::class.java)
      }
      else -> {
        throw unknownSpiType()
      }
    }
  }

  private fun asReaderSpi(): ImageReaderSpi {
    val serviceProvider = instance
    if (serviceProvider !is ImageReaderSpi) {
      throw PluginException(
        "ImageIO SPI '$implementationClass' with type '$READ_SPI_TYPE' must implement ${ImageReaderSpi::class.java.name}.",
        pluginDescriptor.pluginId,
      )
    }
    return serviceProvider
  }

  private fun asWriterSpi(): ImageWriterSpi {
    val serviceProvider = instance
    if (serviceProvider !is ImageWriterSpi) {
      throw PluginException(
        "ImageIO SPI '$implementationClass' with type '$WRITE_SPI_TYPE' must implement ${ImageWriterSpi::class.java.name}.",
        pluginDescriptor.pluginId,
      )
    }
    return serviceProvider
  }

  private fun unknownSpiType(): PluginException {
    return PluginException(
      "Unknown ImageIO SPI type '$type'. Expected '$READ_SPI_TYPE' or '$WRITE_SPI_TYPE'.",
      pluginDescriptor.pluginId,
    )
  }
}
