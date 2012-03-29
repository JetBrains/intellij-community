package com.siyeh.igtest.methodmetrics.cyclomatic_complexity;

public class CyclomaticComplexity
{
    public void fooBar()
    {
        int i = 0;
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        System.out.println("i = " + i);
    }

    public boolean equals(Object o) {
      int i = 0;
      if(bar())
      {
        i++;
      }
      if(bar())
      {
        i++;
      }
      if(bar())
      {
        i++;
      }
      if(bar())
      {
        i++;
      }
      if(bar())
      {
        i++;
      }
      if(bar())
      {
        i++;
      }
      if(bar())
      {
        i++;
      }
      if(bar())
      {
        i++;
      }
      if(bar())
      {
        i++;
      }
      if(bar())
      {
        i++;
      }
      if(bar())
      {
        i++;
      }
      System.out.println("i = " + i);
      return false;
    }

    private boolean bar()
    {
        return true;
    }
}
