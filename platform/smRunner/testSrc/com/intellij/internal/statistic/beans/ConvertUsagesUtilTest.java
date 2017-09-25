package com.intellij.internal.statistic.beans;

import junit.framework.TestCase;

public class ConvertUsagesUtilTest extends TestCase {

  public void testEscapeDescriptorName() {
    ConvertUsagesUtil.assertDescriptorName(ConvertUsagesUtil.escapeDescriptorName("'Copy'_on_Steroids"));
    ConvertUsagesUtil.assertDescriptorName(ConvertUsagesUtil.escapeDescriptorName("Some config name"));
    ConvertUsagesUtil.assertDescriptorName(ConvertUsagesUtil.escapeDescriptorName("\"config\""));
    ConvertUsagesUtil.assertDescriptorName(ConvertUsagesUtil.escapeDescriptorName("StripeButton[JavaEE:App].hidden"));
  }
}