package com.siyeh.igtest.bugs;

public class CovariantComparetoInspection15 implements Comparable<CovariantComparetoInspection15>
{

    public int compareTo(CovariantComparetoInspection15 covariantComparetoInspection15) {
        return 0;
    }
}
