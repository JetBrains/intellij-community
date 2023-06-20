package com.intellij.driver.client.impl;

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
import java.util.HashMap;
import java.util.Map;

public class JmxCallHandler implements InvocationHandler {
  private final JmxHost hostInfo;
  private final String objectName;

  public JmxCallHandler(JmxHost hostInfo, String objectName) {
    this.hostInfo = hostInfo;
    this.objectName = objectName;
  }

  // todo inline for Invoker, get rid of Proxy usage inside of Proxy
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    JMXServiceURL url;
    try {
      url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + hostInfo.getAddress() + "/jmxrmi");
    }
    catch (MalformedURLException e) {
      throw new RuntimeException("Incorrect service URL", e);
    }

    Map<String, Object> properties = new HashMap<>();
    if (hostInfo.getUser() != null) {
      properties.put(JMXConnector.CREDENTIALS, new String[]{hostInfo.getUser(), hostInfo.getPassword()});
    }

    // todo reuse connection for withContext until session cleanup, much faster instead of connect each time
    try (JMXConnector jmxc = JMXConnectorFactory.connect(url, properties)) {
      MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();

      ObjectName mbeanName;
      try {
        mbeanName = new ObjectName(objectName);
      }
      catch (MalformedObjectNameException e) {
        throw new RuntimeException("Incorrect JMX object name", e);
      }

      MBeanServerInvocationHandler wrappedHandler = new MBeanServerInvocationHandler(mbsc, mbeanName);

      return wrappedHandler.invoke(proxy, method, args);
    }
    catch (IOException e) {
      throw new JmxCallException("Unable to perform JMX call", e);
    }
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

    if ("".equals(jmxName.value())) {
      throw new RuntimeException("JmxName.value is empty for " + clazz);
    }

    return (T)Proxy.newProxyInstance(JmxCallHandler.class.getClassLoader(), new Class[]{clazz},
                                     new JmxCallHandler(hostInfo, jmxName.value()));
  }
}