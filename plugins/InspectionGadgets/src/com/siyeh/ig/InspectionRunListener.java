package com.siyeh.ig;

public interface InspectionRunListener{
    void reportRun(String inspectionID, long runTime);
}
