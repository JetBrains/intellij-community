package com.siyeh.igtest.internationalization;

public class MagicCharacterInspection
{
    char m_c = 'c';
    char m_d = 'd';

    static final char s_c = 'c';
    static final char s_d = 'd';

    public MagicCharacterInspection()
    {
    }

    public void foo()
    {
        char c = 'c';
        char d = 'd';


    }

}