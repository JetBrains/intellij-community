package com.siyeh.igtest.bugs;

public class CovariantEqualsInspection2
{
    public CovariantEqualsInspection2()
    {
    }

    public boolean equals(CovariantEqualsInspection2 foo)
    {
        return true;
    }

    public boolean equals(Object foo)
    {
        return true;
    }
}
