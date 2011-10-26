package com.siyeh.ig.bugs;

import com.siyeh.ig.IGInspectionTestCase;

public class MismatchedCollectionQueryUpdateInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/bugs/mismatched_collection_query_update", new MismatchedCollectionQueryUpdateInspection());
  }
}