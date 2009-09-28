package com.siyeh.igtest.internationalization;

public class CharacterComparisonInspection
{
    public CharacterComparisonInspection()
    {
        super();
    }

    public void foo()
    {
        char c = 'c';
        char d = 'd';
        if(c < d)
        {
            return;
        }
        if(c > d)
        {
            return;
        }
        if(c >= d)
        {
            return;
        }
        if(c <= d)
        {
            return;
        }

    }

}