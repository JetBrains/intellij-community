/**
 * (c) 2006 Carp Technologies BV
 * Brouwerijstraat 1, 7523XC Enschede
 * Created: Sep 3, 2007
 */
package com.siyeh.ig.dataflow;

import com.IGInspectionTestCase;

/**
 * @author <A href="bas@carp-technologies.nl">Bas Leijdekkers</a>
 */
public class TooBroadScopeInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/dataflow/scope", new TooBroadScopeInspection());
    }
}