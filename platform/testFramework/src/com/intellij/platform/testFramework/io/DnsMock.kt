// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.io

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.lang.JavaVersion
import org.junit.rules.ExternalResource
import java.io.File
import java.net.InetAddress
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Allows to override results of `InetAddress.get*` methods. Add domains with [add] method.
 *
 * Feel free to suggest a less tricky hack if you know any.
 */
class DnsMock : ExternalResource() {
  private val hostsFile: File by lazy {
    FileUtil.createTempFile("dns-mock-hosts", "txt", true)
  }

  private val delegate: DnsMockDelegate =
    if (JavaVersion.current().isAtLeast(21))
      DnsMockJdk21
    else
      DnsMockJdk17

  public override fun before() {
    delegate.setUp(hostsFile)
  }

  public override fun after() {
    delegate.tearDown()
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

private interface DnsMockDelegate {
  fun setUp(hostsFile: File)
  fun tearDown()
}

private object DnsMockJdk17 : DnsMockDelegate {
  private var oldHostsFile: String? = System.getProperty("jdk.net.hosts.file")
  private var oldService: Any? = null

  private val nameServiceField = InetAddress::class.java.getDeclaredField("nameService").apply { isAccessible = true }
  private val expirySetField = InetAddress::class.java.getDeclaredField("expirySet").apply { isAccessible = true }
  private val cacheField = InetAddress::class.java.getDeclaredField("cache").apply { isAccessible = true }
  private val createNameServiceMethod = InetAddress::class.java.getDeclaredMethod("createNameService").apply { isAccessible = true }

  override fun setUp(hostsFile: File) {
    check(oldService == null)

    oldService = nameServiceField.get(null)
    System.setProperty("jdk.net.hosts.file", hostsFile.absolutePath)
    (expirySetField.get(null) as MutableCollection<*>).clear()
    (cacheField.get(null) as MutableMap<*, *>).clear()
    nameServiceField.set(null, createNameServiceMethod.invoke(null))
  }

  override fun tearDown() {
    check(oldService != null)

    (expirySetField.get(null) as MutableCollection<*>).clear()
    (cacheField.get(null) as MutableMap<*, *>).clear()
    System.setProperty("jdk.net.hosts.file", oldHostsFile ?: "")
    oldService?.let {
      nameServiceField.set(null, it)
    }
    oldService = null
  }
}

private object DnsMockJdk21 : DnsMockDelegate {
  private var oldResolver: Any? = null

  private val resolverLock = InetAddress::class.java.getDeclaredField("RESOLVER_LOCK")
    .apply { isAccessible = true }
    .get(null) as ReentrantLock

  private val resolverField = InetAddress::class.java.getDeclaredField("resolver")
    .apply { isAccessible = true }

  private val cache = InetAddress::class.java.getDeclaredField("cache")
    .apply { isAccessible = true }
    .get(null) as MutableMap<*, *>

  private val expirySet = InetAddress::class.java.getDeclaredField("expirySet")
    .apply { isAccessible = true }
    .get(null) as MutableSet<*>

  private val hostsFileResolverConstructor = InetAddress::class.java
    .declaredClasses
    .single { it.simpleName == "HostsFileResolver" }
    .getConstructor(String::class.java)
    .apply { isAccessible = true }

  override fun setUp(hostsFile: File) {
    check(oldResolver == null)
    cache.clear()
    expirySet.clear()
    resolverLock.withLock {
      oldResolver = resolverField.get(null)
      resolverField.set(null, hostsFileResolverConstructor.newInstance(hostsFile.absolutePath))
    }
  }

  override fun tearDown() {
    check(oldResolver != null)
    cache.clear()
    expirySet.clear()
    resolverLock.withLock {
      resolverField.set(null, oldResolver)
    }
    oldResolver = null
  }
}