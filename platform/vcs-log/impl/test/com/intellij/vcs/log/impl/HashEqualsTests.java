package com.intellij.vcs.log.impl;

import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.HashImpl;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author erokhins
 */
public class HashEqualsTests {

  @Test
  public void testEqualsSelf() throws Exception {
    Hash hash1 = HashImpl.build("adf");
    Assert.assertTrue(hash1.equals(hash1));
  }

  @Test
  public void testEqualsNull() throws Exception {
    Hash hash1 = HashImpl.build("adf");
    Assert.assertFalse(hash1.equals(null));
  }

  @Test
  public void testEquals() throws Exception {
    Hash hash1 = HashImpl.build("adf");
    Hash hash2 = HashImpl.build("adf");
    Assert.assertTrue(hash1.equals(hash2));
  }

  @Test
  public void testEqualsNone() throws Exception {
    Hash hash1 = HashImpl.build("");
    Hash hash2 = HashImpl.build("");
    Assert.assertTrue(hash1.equals(hash2));
  }

}
