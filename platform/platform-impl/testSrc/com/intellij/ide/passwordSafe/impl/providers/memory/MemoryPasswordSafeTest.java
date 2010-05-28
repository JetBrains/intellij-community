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

import com.intellij.util.PathUtil;
import org.junit.Assert;
import org.junit.Test;

import javax.crypto.KeyGenerator;
import java.security.Provider;
import java.security.Security;
import java.util.Map;

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
    try {
      MemoryPasswordSafe p = new MemoryPasswordSafe();
      Assert.assertNull(p.getPassword(null, MemoryPasswordSafeTest.class, "test"));
      p.storePassword(null, MemoryPasswordSafeTest.class, "test", "TEST PASSWORD");
      Assert.assertEquals("TEST PASSWORD", p.getPassword(null, MemoryPasswordSafeTest.class, "test"));
      p.removePassword(null, MemoryPasswordSafeTest.class, "test");
      Assert.assertNull(p.getPassword(null, MemoryPasswordSafeTest.class, "test"));
    } catch(Throwable t) {
      throw new RuntimeException("Test failed: "+diagnosticInfo(), t);
    }
  }

  /**
   * @return diagnostic information for the JCE
   */
  public static String diagnosticInfo() {
    StringBuilder b = new StringBuilder();
    b.append("Security Diagnostic Info:");
    b.append("\n java.home = ").append(System.getProperty("java.home"));
    b.append("\n java.vm.version = ").append(System.getProperty("java.vm.version"));
    b.append("\n java.class.path = ").append(System.getProperty("java.class.path"));
    b.append("\n sun.boot.class.path = ").append(System.getProperty("sun.boot.class.path"));
    b.append("\n java.ext.dirs = ").append(System.getProperty("java.ext.dirs"));
    b.append("\n java.endorsed.dirs = ").append(System.getProperty("java.endorsed.dirs"));
    b.append("\n Providers:");
    for(Provider p : Security.getProviders()) {
      b.append("\n  ").append(p.toString()).append(" info ").append(p.getInfo()).append(" class ").append(p.getClass().getName())
        .append(" location ").append(PathUtil.getJarPathForClass(p.getClass()));
    }
    return b.toString();
  }
}
