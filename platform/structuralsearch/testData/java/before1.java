/*
 * Created on Jan 30, 2004
 *
 */
package somepackage;

/**
 * Title: ForecastSummaryReportTest
 *
 *
 */
public class ForecastSummaryReportTest extends SOTestCase {

    ForecastSummaryReport oReport = null;
    ScheduleGroupData oSkdgrp = null;
    SODate oStart = null;
    SODate oEnd = null;
    SODateInterval oDateRange = null;


    /**
     * Constructor for ForecastSummaryReportTest.
     * @param arg0
     */
    public ForecastSummaryReportTest(String arg0) {
        super(arg0);
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(ForecastSummaryReportTest.class);
    }

    /*
     * @see TestCase#setUp()
     */
    protected void setUp() throws Exception {
        super.setUp();
        try {
            oSkdgrp = ScheduleGroupData.getScheduleGroupData(new Integer(1001));
            oDateRange = new SODateInterval(new SODate("01/23/2004"), new SODate("01/30/2004"));
            oReport = new ForecastSummaryReport(oSkdgrp.getCorporateEntity(),oDateRange);
        } catch (Exception e) {
            System.out.println("Unhandled exception in Setup:" + e);
            fail();
        }

    }

    /*
     * @see TestCase#tearDown()
     */
    protected void tearDown() throws Exception {
        super.tearDown();
        oReport = null;
    }

    /*
     * Cannot test protected methods from this location
     */
    public void testGenerate() {
    }

    public void testForecastSummaryReport() {
        try {
            ForecastSummaryReport testReport = new ForecastSummaryReport(oSkdgrp.getCorporateEntity(),oDateRange);
            assertNotNull(testReport);
        } catch (RetailException e) {
            e.printStackTrace();
            fail("RetailException: Could not create ForecastSummaryReport(CorporateEntity, SODateInterval)");
        }
    }

    public void testSetIncludeSublocationsFlag() {
        oReport.setIncludeSublocationsFlag(true);
        assertTrue(oReport.isIncludeSublocationsFlagSet());
    }

    public void testIsIncludeSublocationsFlagSet() {
        oReport.setIncludeSublocationsFlag(false);
        assertFalse(oReport.isIncludeSublocationsFlagSet());
        oReport.setIncludeSublocationsFlag(true);
        assertTrue(oReport.isIncludeSublocationsFlagSet());
    }

    public void testRefresh() throws RetailException {
        oReport.refresh();
        assertNotNull(oReport);
    }
}
