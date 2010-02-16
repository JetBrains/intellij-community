/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.execution.junit2.segments;

import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.info.TestInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.rt.execution.junit.segments.PoolOfDelimiters;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

public class InputObjectRegistry {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.segments.InputObjectRegistryImpl");
  private final Map<String, TestProxy> myKnownObjects = new HashMap<String,TestProxy>();

  public TestProxy getByKey(final String key) {
    final TestProxy result = myKnownObjects.get(key);
    if (result == null) {
      LOG.error("Unknwon key: " + key);
      LOG.info("Known keys:");
      final ArrayList<String> knownKeys = new ArrayList<String>(myKnownObjects.keySet());
      Collections.sort(knownKeys);
      LOG.info(knownKeys.toString());
    }
    return result;
  }

  public void readPacketFrom(final ObjectReader reader) {
    final String reference = reader.upTo(PoolOfDelimiters.REFERENCE_END);
    if (myKnownObjects.containsKey(reference)) return;
    final TestProxy test = new TestProxy(TestInfo.readInfoFrom(reader));
    myKnownObjects.put(reference, test);
  }

}
