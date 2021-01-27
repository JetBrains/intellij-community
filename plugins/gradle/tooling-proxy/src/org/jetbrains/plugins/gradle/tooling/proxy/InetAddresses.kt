// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.proxy;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class InetAddresses {
  private static final int REACHABLE_TIMEOUT_MS = 50;
  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private final List<InetAddress> loopback = new ArrayList();
  private final List<InetAddress> remote = new ArrayList();

  InetAddresses() throws SocketException {
    this.analyzeNetworkInterfaces();
  }

  private void analyzeNetworkInterfaces() throws SocketException {
    Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
    if (interfaces != null) {
      while(interfaces.hasMoreElements()) {
        this.analyzeNetworkInterface((NetworkInterface)interfaces.nextElement());
      }
    }

  }

  private void analyzeNetworkInterface(NetworkInterface networkInterface) {
    this.logger.debug("Adding IP addresses for network interface {}", networkInterface.getDisplayName());

    try {
      boolean isLoopbackInterface = networkInterface.isLoopback();
      this.logger.debug("Is this a loopback interface? {}", isLoopbackInterface);
      Enumeration candidates = networkInterface.getInetAddresses();

      while(candidates.hasMoreElements()) {
        InetAddress candidate = (InetAddress)candidates.nextElement();
        if (isLoopbackInterface) {
          if (candidate.isLoopbackAddress()) {
            if (candidate.isReachable(REACHABLE_TIMEOUT_MS)) {
              this.logger.debug("Adding loopback address {}", candidate);
              this.loopback.add(candidate);
            } else {
              this.logger.debug("Ignoring unreachable local address on loopback interface {}", candidate);
            }
          } else {
            this.logger.debug("Ignoring remote address on loopback interface {}", candidate);
          }
        } else if (candidate.isLoopbackAddress()) {
          this.logger.debug("Ignoring loopback address on remote interface {}", candidate);
        } else {
          this.logger.debug("Adding remote address {}", candidate);
          this.remote.add(candidate);
        }
      }
    } catch (SocketException var5) {
      this.logger.debug("Error while querying interface {} for IP addresses", networkInterface, var5);
    } catch (Throwable var6) {
      throw new RuntimeException(String.format("Could not determine the IP addresses for network interface %s", networkInterface.getName()), var6);
    }

  }

  public List<InetAddress> getLoopback() {
    return this.loopback;
  }

  public List<InetAddress> getRemote() {
    return this.remote;
  }
}
