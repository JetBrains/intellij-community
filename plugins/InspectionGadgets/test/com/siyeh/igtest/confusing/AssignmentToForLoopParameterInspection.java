package com.siyeh.igtest.confusing;

public class AssignmentToForLoopParameterInspection
{
    public AssignmentToForLoopParameterInspection()
    {
    }

    public void fooBar(int bar, int baz)
    {

        for(int i = 0; i < 5; i++)
        {
            for(int j = 0; j < 5; i++)
            {

            }
        }
        for(int j = 0; j < 5; j++)
        {
            j = 2;
        }
        for(int k = 0; k < 5; k++)
        {
            k++;
            k--;
            ++k;
            --k;
        }
    }
}
