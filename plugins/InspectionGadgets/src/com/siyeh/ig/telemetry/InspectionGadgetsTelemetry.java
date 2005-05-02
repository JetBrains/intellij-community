package com.siyeh.ig.telemetry;

import com.siyeh.ig.InspectionRunListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;

public class InspectionGadgetsTelemetry implements InspectionRunListener{
    private final Map<String,Integer> totalRunCount = new HashMap<String, Integer>(400);
    private final Map<String,Long> totalRunTime = new HashMap<String, Long>(400);
    private final Object lock = new Object();

    public void reportRun(String inspectionID, long runTime)
    {
        synchronized(lock)
        {
            final Integer count = totalRunCount.get(inspectionID);
            if(count == null){
                totalRunCount.put(inspectionID, 1);
            } else{
                totalRunCount.put(inspectionID,
                                  count + 1);
            }
            final Long runTimeSoFar = totalRunTime.get(inspectionID);
            if(runTimeSoFar == null){
                totalRunTime.put(inspectionID, runTime);
            } else{
                totalRunTime.put(inspectionID,
                                 runTimeSoFar + runTime);
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
            final Long runTime = totalRunTime.get(inspectionID);
            if(runTime == null)
            {
                return 0L;
            }
            return runTime;
        }
    }

    public int getRunCountForInspection(String inspectionID)
    {
        synchronized(lock){
            final Integer runCount = totalRunCount.get(inspectionID);
            if(runCount == null)
            {
                return 0;
            }
            return runCount;
        }
    }

    public String[] getInspections()
    {
        synchronized(lock)
        {
            final Set<String> inspections = totalRunCount.keySet();
            final int numInspections = inspections.size();
            final String[] inspectionArray = inspections.toArray(new String[numInspections]);
            Arrays.sort(inspectionArray);
            return inspectionArray;
        }
    }

    public double getAverageRunTimeForInspection(String inspectionID){
        synchronized(lock){
            final Integer runCount = totalRunCount.get(inspectionID);
            if(runCount == null){
                return 0.0;
            }
            final Long runTime = totalRunTime.get(inspectionID);

            return  (double) (runTime) / (double) (runCount);
        }
    }
}
