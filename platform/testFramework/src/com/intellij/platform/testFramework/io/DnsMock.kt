// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.io

import com.intellij.openapi.util.io.FileUtil
import org.junit.rules.ExternalResource
import java.io.File
import java.net.InetAddress

/**
 * Allows to override results of `InetAddress.get*` methods. Add domains with [add] method.
 *
 * Feel free to suggest a less tricky hack if you know any.
 */
class DnsMock : ExternalResource() {
  private val nameServiceField = InetAddress::class.java.getDeclaredField("nameService")
  private val expirySetField = InetAddress::class.java.getDeclaredField("expirySet")
  private val cacheField = InetAddress::class.java.getDeclaredField("cache")
  private val createNameServiceMethod = InetAddress::class.java.getDeclaredMethod("createNameService")

  private var oldService: Any? = null
  private var oldHostsFile: String? = System.getProperty("jdk.net.hosts.file")
  private val hostsFile: File by lazy {
    FileUtil.createTempFile("dns-mock-hosts", "txt", true)
  }

  public override fun before() {
    check(oldService == null)

    nameServiceField.isAccessible = true
    expirySetField.isAccessible = true
    cacheField.isAccessible = true
    createNameServiceMethod.isAccessible = true

    oldService = nameServiceField.get(null)
    System.setProperty("jdk.net.hosts.file", hostsFile.absolutePath)
    (expirySetField.get(null) as MutableCollection<*>).clear()
    (cacheField.get(null) as MutableMap<*, *>).clear()
    nameServiceField.set(null, createNameServiceMethod.invoke(null))
  }

  public override fun after() {
    (expirySetField.get(null) as MutableCollection<*>).clear()
    (cacheField.get(null) as MutableMap<*, *>).clear()
    System.setProperty("jdk.net.hosts.file", oldHostsFile ?: "")
    oldService?.let {
      nameServiceField.set(null, it)
    }
  }

  fun add(domain: String, vararg addresses: InetAddress): DnsMock {
    hostsFile.appendText(addresses.joinToString(separator = "\n", prefix = "\n") { "${it.hostAddress} $domain" })
    return this
  }

  inline fun <T> use(body: (DnsMock) -> T): T {
    before()
    return AutoCloseable(::after).use { body(this) }
  }
}