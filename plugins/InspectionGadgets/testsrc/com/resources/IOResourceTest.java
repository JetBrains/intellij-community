package com.resources;

import com.IGInspectionTestCase;
import com.siyeh.ig.resources.IOResourceInspection;

/**
 * @author Alexey
 */
public class IOResourceTest extends IGInspectionTestCase {
  public void test() throws Exception {
    doTest("com/siyeh/igtest/resources/io", new IOResourceInspection());
  }
}
