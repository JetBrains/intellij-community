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
        if(<warning descr="Character comparison 'c < d' in an internationalized context">c < d</warning>)
        {
            return;
        }
        if(<warning descr="Character comparison 'c > d' in an internationalized context">c > d</warning>)
        {
            return;
        }
        if(<warning descr="Character comparison 'c >= d' in an internationalized context">c >= d</warning>)
        {
            return;
        }
        if(<warning descr="Character comparison 'c <= d' in an internationalized context">c <= d</warning>)
        {
            return;
        }
        if (c == d) return;
        if (c <<error descr="Expression expected"> </error>) return;
        @org.jetbrains.annotations.NonNls char a = 'a';
        if (c < a) return;
    }

}