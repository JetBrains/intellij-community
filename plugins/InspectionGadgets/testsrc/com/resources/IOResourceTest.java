package com.resources;

import com.siyeh.ig.IGInspectionTestCase;
import com.siyeh.ig.resources.IOResourceInspection;

/**
 * @author Alexey
 */
public class IOResourceTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/resources/io/plain", new IOResourceInspection());
    }

    public void testInsideTry() throws Exception {
        final IOResourceInspection inspection = new IOResourceInspection();
        inspection.insideTryAllowed = true;
        doTest("com/siyeh/igtest/resources/io/inside_try", inspection);
    }
}
