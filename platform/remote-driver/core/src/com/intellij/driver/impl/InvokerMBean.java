package com.intellij.driver.impl;

import com.intellij.driver.model.transport.RemoteCall;
import com.intellij.driver.model.transport.RemoteCallResult;

import javax.management.*;
import java.lang.management.ManagementFactory;

@SuppressWarnings("unused")
public interface InvokerMBean {
  void ping();

  RemoteCallResult invoke(RemoteCall call);

  int newSession();

  void cleanup(int sessionId);

  static void register() throws JMException {
    ObjectName objectName = new ObjectName("com.intellij.driver:type=Invoker");
    MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    server.registerMBean(new Invoker(), objectName);
  }
}
