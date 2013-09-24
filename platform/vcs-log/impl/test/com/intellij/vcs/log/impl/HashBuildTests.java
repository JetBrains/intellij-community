package com.intellij.vcs.log.impl;


import com.intellij.vcs.log.Hash;
import junit.framework.Assert;
import org.junit.Test;

/**
 * @author erokhins
 */
public class HashBuildTests {

  public void runStringTest(String strHash) {
    Hash hash = HashImpl.build(strHash);
    Assert.assertEquals(strHash, hash.asString());
  }

  @Test
  public void testBuildNone() throws Exception {
    runStringTest("");
  }

  @Test
  public void testBuild0() throws Exception {
    runStringTest("0");
  }

  @Test
  public void testBuild000() throws Exception {
    runStringTest("0000");
  }

  @Test
  public void testBuild1() throws Exception {
    runStringTest("1");
  }

  @Test
  public void testBuildSomething() throws Exception {
    runStringTest("ff01a");
  }

  @Test
  public void testBuildEven() throws Exception {
    runStringTest("1133");
  }

  @Test
  public void testBuildOdd() throws Exception {
    runStringTest("ffa");
  }

  @Test
  public void testBuildLongOdd() throws Exception {
    runStringTest("ff01a123125afabcdef12345678900987654321");
  }

  @Test
  public void testBuildLongEven() throws Exception {
    runStringTest("ff01a123125afabcdef123456789009876543219");
  }


}
