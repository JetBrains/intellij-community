// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.proxy

import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.*

internal class InetAddresses {
  val loopback: MutableList<InetAddress> = mutableListOf()
  val remote: MutableList<InetAddress> = mutableListOf()

  @Throws(SocketException::class)
  private fun analyzeNetworkInterfaces() {
    val interfaces = NetworkInterface.getNetworkInterfaces()
    if (interfaces != null) {
      while (interfaces.hasMoreElements()) {
        analyzeNetworkInterface(interfaces.nextElement() as NetworkInterface)
      }
    }
  }

  private fun analyzeNetworkInterface(networkInterface: NetworkInterface) {
    logger.debug("Adding IP addresses for network interface {}", networkInterface.displayName)
    try {
      val isLoopbackInterface = networkInterface.isLoopback
      logger.debug("Is this a loopback interface? {}", isLoopbackInterface)
      val candidates: Enumeration<*> = networkInterface.inetAddresses
      while (candidates.hasMoreElements()) {
        val candidate = candidates.nextElement() as InetAddress
        if (isLoopbackInterface) {
          if (candidate.isLoopbackAddress) {
            if (candidate.isReachable(REACHABLE_TIMEOUT_MS)) {
              logger.debug("Adding loopback address {}", candidate)
              loopback.add(candidate)
            }
            else {
              logger.debug("Ignoring unreachable local address on loopback interface {}", candidate)
            }
          }
          else {
            logger.debug("Ignoring remote address on loopback interface {}", candidate)
          }
        }
        else if (candidate.isLoopbackAddress) {
          logger.debug("Ignoring loopback address on remote interface {}", candidate)
        }
        else {
          logger.debug("Adding remote address {}", candidate)
          remote.add(candidate)
        }
      }
    }
    catch (e: SocketException) {
      logger.debug("Error while querying interface {} for IP addresses", networkInterface, e)
    }
    catch (t: Throwable) {
      throw RuntimeException(String.format("Could not determine the IP addresses for network interface %s", networkInterface.name), t)
    }
  }

  companion object {
    private val logger = LoggerFactory.getLogger(InetAddresses::class.java)
    private const val REACHABLE_TIMEOUT_MS = 50
  }

  init {
    analyzeNetworkInterfaces()
  }
}