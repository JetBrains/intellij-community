package com.intellij.driver.impl;

import com.intellij.driver.model.ProductVersion;
import com.intellij.driver.model.transport.RemoteCall;
import com.intellij.driver.model.transport.RemoteCallResult;
import com.intellij.platform.diagnostic.telemetry.IJTracer;
import io.opentelemetry.context.Context;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.function.Consumer;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public interface InvokerMBean {
  ProductVersion getProductVersion();

  boolean isApplicationInitialized();

  void exit();

  RemoteCallResult invoke(RemoteCall call);

  int newSession();

  void cleanup(int sessionId);

  void takeScreenshot(@Nullable String outFolder);

  static void register(@NotNull Supplier<? extends IJTracer> tracerSupplier,
                       @NotNull Supplier<? extends Context> timedContextSupplier,
                       @NotNull Consumer<String> screenshotAction) throws JMException {
    ObjectName objectName = new ObjectName("com.intellij.driver:type=Invoker");
    MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    server.registerMBean(new Invoker(tracerSupplier, timedContextSupplier, screenshotAction), objectName);
  }
}
