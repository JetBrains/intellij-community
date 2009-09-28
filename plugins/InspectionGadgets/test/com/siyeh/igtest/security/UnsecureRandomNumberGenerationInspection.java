package com.siyeh.igtest.security;

import java.util.Random;

public class UnsecureRandomNumberGenerationInspection
{
    public void foo()
    {
       new Random();
       new java.util.Random();
       Math.random();
    }
}
