package com.intellij.ide.passwordSafe.impl.providers.masterKey.windows;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.ide.passwordSafe.MasterPasswordUnavailableException;
import org.junit.Assert;
import org.junit.Test;

import java.security.SecureRandom;

/**
 * The test for windows crypt utilities
 */
public class WindowsCryptUtilTest {
  @Test
  public void testProtect() throws MasterPasswordUnavailableException {
    if(SystemInfo.isWindows) {
      SecureRandom t = new SecureRandom();
      byte[] data = new byte[256];
      t.nextBytes(data);
      byte[] encrypted = WindowsCryptUtils.protect(data);
      byte[] decrypted = WindowsCryptUtils.unprotect(encrypted);
      Assert.assertArrayEquals(data, decrypted);
    }
  }
}
