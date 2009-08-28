/*
 * @author max
 */
package com.intellij.testFramework.fixtures.impl;

import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaTestFixture;
import junit.framework.Assert;

public class BaseFixture extends UsefulTestCase implements IdeaTestFixture {
  private boolean myDisposed;
  private boolean myInitialized;

  public void setUp() throws Exception {
    super.setUp();
    Assert.assertFalse("setUp() already has been called", myInitialized);
    Assert.assertFalse("tearDown() already has been called", myDisposed);
    myInitialized = true;
  }

  public void tearDown() throws Exception {
    Assert.assertTrue("setUp() has not been called", myInitialized);
    Assert.assertFalse("tearDown() already has been called", myDisposed);
    myDisposed = true;
    super.tearDown();
    resetClassFields(getClass());
  }

  private void resetClassFields(final Class<?> aClass) {
    try {
      UsefulTestCase.clearDeclaredFields(this, aClass);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }

    if (aClass == BaseFixture.class) return;
    resetClassFields(aClass.getSuperclass());
  }

}