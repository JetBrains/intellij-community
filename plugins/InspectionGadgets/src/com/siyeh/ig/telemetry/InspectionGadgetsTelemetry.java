package com.siyeh.ig.telemetry;

import com.siyeh.ig.InspectionRunListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;

public class InspectionGadgetsTelemetry implements InspectionRunListener{
    private final Map totalRunCount = new HashMap(400);
    private final Map totalRunTime = new HashMap(400);
    private final Object lock = new Object();

    public void reportRun(String inspectionID, long runTime)
    {
        synchronized(lock)
        {
            final Integer count = (Integer) totalRunCount.get(inspectionID);
            if(count == null){
                totalRunCount.put(inspectionID, new Integer(1));
            } else{
                totalRunCount.put(inspectionID,
                                  new Integer(count.intValue() + 1));
            }
            final Long runTimeSoFar = (Long) totalRunTime.get(inspectionID);
            if(runTimeSoFar == null){
                totalRunTime.put(inspectionID, new Long(runTime));
            } else{
                totalRunTime.put(inspectionID,
                                 new Long(runTimeSoFar.intValue() + runTime));
            }
        }
    }

    public void reset()
    {
        synchronized(lock){
            totalRunCount.clear();
            totalRunTime.clear();
        }
    }

    public long getRunTimeForInspection(String inspectionID)
    {
        synchronized(lock){
            final Long runTime = (Long) totalRunTime.get(inspectionID);
            if(runTime == null)
            {
                return 0L;
            }
            return runTime.longValue();
        }
    }

    public int getRunCountForInspection(String inspectionID)
    {
        synchronized(lock){
            final Integer runCount = (Integer) totalRunCount.get(inspectionID);
            if(runCount == null)
            {
                return 0;
            }
            return runCount.intValue();
        }
    }

    public String[] getInspections()
    {
        synchronized(lock)
        {
            final Set inspections = totalRunCount.keySet();
            final int numInspections = inspections.size();
            final String[] inspectionArray = (String[]) inspections.toArray(new String[numInspections]);
            Arrays.sort(inspectionArray);
            return inspectionArray;
        }
    }

    public double getAverageRunTimeForInspection(String inspectionID){
        synchronized(lock){
            final Integer runCount = (Integer) totalRunCount.get(inspectionID);
            if(runCount == null){
                return 0.0;
            }
            final Long runTime = (Long) totalRunTime.get(inspectionID);

            return  (double) runTime.longValue() / (double) runCount.intValue();
        }
    }
}
