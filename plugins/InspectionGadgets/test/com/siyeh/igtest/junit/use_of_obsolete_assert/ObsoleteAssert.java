package com.siyeh.igtest.junit.use_of_obsolete_assert;

public class ObsoleteAssert {

  public void testMe(int s) {
    junit.framework.Assert.<warning descr="Call to 'assertEquals()' from 'junit.framework.Assert' should be replaced with call to method from 'org.junit.Assert'">assertEquals</warning>("asdfasd", -1, s);
  }
}
