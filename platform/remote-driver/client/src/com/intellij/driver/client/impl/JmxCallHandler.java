package com.intellij.driver.client.impl;

import org.jetbrains.annotations.NotNull;

import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class JmxCallHandler implements InvocationHandler {
  static final class ClosedException extends IllegalStateException {
    ClosedException() {
      super("JMX call handler is closed");
    }
  }

  private final JmxHost hostInfo;
  private final ObjectName mbeanName;
  private final Object callLock = new Object();
  private final Object connectorStateLock = new Object();
  private JMXConnector currentConnector;
  private boolean closed;

  public JmxCallHandler(JmxHost hostInfo, String objectName) {
    this.hostInfo = hostInfo;

    try {
      this.mbeanName = new ObjectName(objectName);
    }
    catch (MalformedObjectNameException e) {
      throw new RuntimeException("Incorrect JMX object name: " + objectName, e);
    }
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if ("close".equals(method.getName())) {
      closePermanently();
      return null;
    }

    synchronized (callLock) {
      return invokeCall(proxy, method, args);
    }
  }

  private Object invokeCall(Object proxy, Method method, Object[] args) throws Throwable {
    JMXConnector connector = getOrCreateConnector();

    try {
      MBeanServerConnection mbsc = connector.getMBeanServerConnection();

      MBeanServerInvocationHandler wrappedHandler = new MBeanServerInvocationHandler(mbsc, mbeanName);
      return wrappedHandler.invoke(proxy, method, args);
    }
    catch (IOException e) {
      discardConnector(connector);
      throw new JmxCallException("Unable to perform JMX call: " + method + "(" + (args != null ? Arrays.asList(args) : "null") + ")", e);
    }
  }

  private JMXConnector getOrCreateConnector() {
    synchronized (connectorStateLock) {
      checkNotClosed();
      if (currentConnector != null) {
        return currentConnector;
      }
    }

    JMXConnector connector;
    ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
      connector = getConnector();
    }
    catch (IOException e) {
      throw new JmxCallException("Unable to connect to JMX host: " + getServiceTextURL(), e);
    }
    finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader);
    }

    synchronized (connectorStateLock) {
      if (closed) {
        closeIgnoringFailure(connector);
        throw new ClosedException();
      }
      currentConnector = connector;
      return connector;
    }
  }

  private void closePermanently() {
    JMXConnector connector;
    synchronized (connectorStateLock) {
      if (closed) {
        return;
      }
      closed = true;
      connector = currentConnector;
      currentConnector = null;
    }
    closeIgnoringFailure(connector);
  }

  private void discardConnector(JMXConnector connector) {
    synchronized (connectorStateLock) {
      if (currentConnector == connector) {
        currentConnector = null;
      }
    }
    closeIgnoringFailure(connector);
  }

  private void checkNotClosed() {
    if (closed) {
      throw new ClosedException();
    }
  }

  private static void closeIgnoringFailure(JMXConnector connector) {
    if (connector == null) {
      return;
    }
    try {
      connector.close();
    }
    catch (IOException ignored) {
    }
  }

  public JMXConnector getConnector() throws IOException {
    JMXServiceURL url;
    var textUrl = getServiceTextURL();
    try {
      url = new JMXServiceURL(textUrl);
    }
    catch (MalformedURLException e) {
      throw new RuntimeException("Incorrect service URL: " + textUrl, e);
    }

    Map<String, Object> properties = new HashMap<>();
    if (hostInfo.getUser() != null) {
      properties.put(JMXConnector.CREDENTIALS, new String[]{hostInfo.getUser(), hostInfo.getPassword()});
    }

    return JMXConnectorFactory.connect(url, properties);
  }

  private @NotNull String getServiceTextURL() {
    return "service:jmx:rmi:///jndi/rmi://" + hostInfo.getAddress() + "/jmxrmi";
  }

  public static <T> T jmx(Class<T> clazz) {
    return jmx(clazz, new JmxHost(null, null, "localhost:7777"));
  }

  @SuppressWarnings("unchecked")
  public static <T> T jmx(Class<T> clazz, JmxHost hostInfo) {
    JmxName jmxName = clazz.getAnnotation(JmxName.class);
    if (jmxName == null) {
      throw new RuntimeException("There is no @JmxName annotation for " + clazz);
    }

    if (jmxName.value().isEmpty()) {
      throw new RuntimeException("JmxName.value is empty for " + clazz);
    }

    return (T)Proxy.newProxyInstance(JmxCallHandler.class.getClassLoader(), new Class[]{clazz, AutoCloseable.class},
                                     new JmxCallHandler(hostInfo, jmxName.value()));
  }
}
