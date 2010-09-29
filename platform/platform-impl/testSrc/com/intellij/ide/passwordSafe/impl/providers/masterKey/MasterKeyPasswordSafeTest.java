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
package com.intellij.ide.passwordSafe.impl.providers.masterKey;

import com.intellij.ide.passwordSafe.PasswordSafeException;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The test for master key password safe
 */
public class MasterKeyPasswordSafeTest {

  @Test
  public void testMasterKey() throws PasswordSafeException {
    PasswordDatabase db = new PasswordDatabase();
    MasterKeyPasswordSafe s1 = testProvider(db);
    s1.resetMasterPassword("pass1", false);
    s1.storePassword(null, MasterKeyPasswordSafeTest.class, "TEST", "test");
    assertEquals("test", s1.getPassword(null, MasterKeyPasswordSafeTest.class, "TEST"));
    assertTrue(s1.changeMasterPassword("pass1", "pass2", false));
    assertEquals("test", s1.getPassword(null, MasterKeyPasswordSafeTest.class, "TEST"));
    MasterKeyPasswordSafe s2 = testProvider(db);
    assertFalse(s2.setMasterPassword("pass1"));
    assertTrue(s2.setMasterPassword("pass2"));
    assertEquals("test", s2.getPassword(null, MasterKeyPasswordSafeTest.class, "TEST"));
    assertTrue(s2.changeMasterPassword("pass2", "pass3", false));
    assertTrue(s2.changeMasterPassword("pass3", "pass4", false));
    assertTrue(s2.setMasterPassword("pass4"));
    assertEquals("test", s2.getPassword(null, MasterKeyPasswordSafeTest.class, "TEST"));
    s2.resetMasterPassword("fail", false);
    assertNull(s2.getPassword(null, MasterKeyPasswordSafeTest.class, "TEST"));
  }

  /**
   * Get test instance of the provider
   * @param db the database to use
   * @return a instance of the provider
   */
  private static MasterKeyPasswordSafe testProvider(final PasswordDatabase db) {
    return new MasterKeyPasswordSafe(db) {
      @Override
      protected boolean isTestMode() {
        return true;
      }
    };
  }

}
