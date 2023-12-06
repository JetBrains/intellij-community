package com.intellij.driver.impl;

import com.intellij.driver.model.ProductVersion;
import com.intellij.driver.model.transport.RemoteCall;
import com.intellij.driver.model.transport.RemoteCallResult;
import com.intellij.platform.diagnostic.telemetry.IJTracer;
import io.opentelemetry.context.Context;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public interface InvokerMBean {
  ProductVersion getProductVersion();

  boolean isApplicationInitialized();

  void exit();

  RemoteCallResult invoke(RemoteCall call);

  int newSession();

  void cleanup(int sessionId);

  static void register(Supplier<? extends IJTracer> tracerSupplier, Supplier<? extends Context> timedContextSupplier) throws JMException {
    ObjectName objectName = new ObjectName("com.intellij.driver:type=Invoker");
    MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    server.registerMBean(new Invoker(tracerSupplier, timedContextSupplier), objectName);
  }
}
