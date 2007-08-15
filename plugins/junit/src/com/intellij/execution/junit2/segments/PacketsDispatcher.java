package com.intellij.execution.junit2.segments;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.rt.execution.junit.segments.PacketProcessor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class PacketsDispatcher implements PacketProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.segments.PacketsDispatcher");
  private final List<PacketConsumer> myListeners = new CopyOnWriteArrayList<PacketConsumer>();
  private final InputObjectRegistry myObjectRegistry;

  public PacketsDispatcher() {
    this(new InputObjectRegistryImpl());
  }

  public PacketsDispatcher(final InputObjectRegistry objectRegistry) {
    myObjectRegistry = objectRegistry;
    addListener(objectRegistry);
  }

  public void addListener(final PacketConsumer objectConsumer) {
    if (myListeners.contains(objectConsumer)) return;
    myListeners.add(objectConsumer);
  }

  public void processPacket(final String packet) {
    assertIsDispatchThread();
    for (final PacketConsumer listener : myListeners) {
      final String prefix = listener.getPrefix();
      if (packet.startsWith(prefix)) {
        try {
          listener.readPacketFrom(new ObjectReader(packet, prefix.length(), myObjectRegistry));
        }
        catch (Throwable e) {
          LOG.error("Dispatching: " + packet, e);
        }
      }
    }
  }

  public static void assertIsDispatchThread() {
    final Application application = ApplicationManager.getApplication();
    if (application != null && !application.isUnitTestMode())
      application.assertIsDispatchThread();
  }

  public void onFinished() {
    for (final PacketConsumer listener : myListeners) {
      listener.onFinished();
    }
  }
}

