package com.siyeh.igtest.internationalization.character_comparison;

public class CharacterComparison
{
    public CharacterComparison()
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
        if (c == d) return;
        if (c < ) return;
        @org.jetbrains.annotations.NonNls char a = 'a';
        if (c < a) return;
    }

}