package com.intellij.execution.junit2.segments;

import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.info.TestInfoFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.rt.execution.junit.segments.PoolOfDelimiters;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

public class InputObjectRegistryImpl implements InputObjectRegistry {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.segments.InputObjectRegistryImpl");
  private final Map<String, TestProxy> myKnownObjects = new HashMap<String,TestProxy>();

  public TestProxy getByKey(final String key) {
    final TestProxy result = myKnownObjects.get(key);
    if (result == null) {
      LOG.assertTrue(false, "Unknwon key: " + key);
      LOG.info("Known keys:");
      final ArrayList<String> knownKeys = new ArrayList<String>(myKnownObjects.keySet());
      Collections.sort(knownKeys);
      LOG.info(knownKeys.toString());
    }
    return result;
  }

  public String getPrefix() {
    return PoolOfDelimiters.OBJECT_PREFIX;
  }

  public void readPacketFrom(final ObjectReader reader) {
    final String reference = reader.upTo(PoolOfDelimiters.REFERENCE_END);
    if (myKnownObjects.containsKey(reference)) return;
    final TestProxy test = new TestProxy(TestInfoFactory.readInfoFrom(reader));
    myKnownObjects.put(reference, test);
  }

  public void onFinished() {
  }
}
