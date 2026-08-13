// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.driver.client.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
class JmxCallHandlerTest {
  @Test
  void closeInterruptsBlockedCallAndPermanentlyClosesHandler() throws Exception {
    CountDownLatch callEntered = new CountDownLatch(1);
    CountDownLatch connectorClosed = new CountDownLatch(1);
    AtomicInteger connectorCreations = new AtomicInteger();
    MBeanServerConnection connection = (MBeanServerConnection)Proxy.newProxyInstance(
      getClass().getClassLoader(),
      new Class[]{MBeanServerConnection.class},
      (proxy, method, args) -> {
        if (method.getName().equals("invoke")) {
          callEntered.countDown();
          if (!connectorClosed.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("connector was not closed");
          }
          throw new IOException("connector closed");
        }
        return defaultValue(method.getReturnType());
      }
    );
    JMXConnector connector = (JMXConnector)Proxy.newProxyInstance(
      getClass().getClassLoader(),
      new Class[]{JMXConnector.class},
      (proxy, method, args) -> switch (method.getName()) {
        case "getMBeanServerConnection" -> connection;
        case "close" -> {
          connectorClosed.countDown();
          yield null;
        }
        case "getConnectionId" -> "test";
        default -> defaultValue(method.getReturnType());
      }
    );
    JmxCallHandler handler = new JmxCallHandler(new JmxHost(null, null, "unused"), "test:type=Blocking") {
      @Override
      public JMXConnector getConnector() {
        connectorCreations.incrementAndGet();
        return connector;
      }
    };
    BlockingMBean mbean = (BlockingMBean)Proxy.newProxyInstance(
      getClass().getClassLoader(),
      new Class[]{BlockingMBean.class, AutoCloseable.class},
      handler
    );
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<String> blockedCall = executor.submit(mbean::block);
      assertThat(callEntered.await(5, TimeUnit.SECONDS)).isTrue();

      mbean.close();

      assertThatThrownBy(() -> blockedCall.get(5, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(JmxCallException.class);
      assertThatThrownBy(mbean::block)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("JMX call handler is closed");
      assertThat(connectorCreations).hasValue(1);
    }
    finally {
      executor.shutdownNow();
    }
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) return null;
    if (type == boolean.class) return false;
    if (type == char.class) return '\0';
    if (type == byte.class) return (byte)0;
    if (type == short.class) return (short)0;
    if (type == int.class) return 0;
    if (type == long.class) return 0L;
    if (type == float.class) return 0F;
    if (type == double.class) return 0D;
    return null;
  }

  private interface BlockingMBean extends AutoCloseable {
    String block();
  }
}
