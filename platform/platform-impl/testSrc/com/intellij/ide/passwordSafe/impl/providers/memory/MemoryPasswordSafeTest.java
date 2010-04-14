/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide.passwordSafe.impl.providers.memory;

import org.junit.Assert;
import org.junit.Test;

/**
 * The test for memory implementation of password safe
 */
public class MemoryPasswordSafeTest {

  /**
   * Test basic operation of memory-based provider
   *
   * @throws Exception in case of problem
   */
  @Test
  public void testMemory() throws Exception {
    MemoryPasswordSafe p = new MemoryPasswordSafe();
    Assert.assertNull(p.getPassword(null, MemoryPasswordSafeTest.class, "test"));
    p.storePassword(null, MemoryPasswordSafeTest.class, "test", "TEST PASSWORD");
    Assert.assertEquals("TEST PASSWORD", p.getPassword(null, MemoryPasswordSafeTest.class, "test"));
    p.removePassword(null, MemoryPasswordSafeTest.class, "test");
    Assert.assertNull(p.getPassword(null, MemoryPasswordSafeTest.class, "test"));
  }
}
