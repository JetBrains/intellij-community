package com.siyeh.igtest.threading;

public class BusyWaitInspection
{
   public void fooBar()
   {
       while(true)
       {
           try
           {
               Thread.sleep(1L);
           }
           catch(InterruptedException e)
           {
               e.printStackTrace();
           }
       }
   }
}
