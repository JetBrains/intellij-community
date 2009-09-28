package com.siyeh.igtest.bugs;

public class EmptyStatementBodyInspection
{
    private void foo()
    {
        while(bar());
        while(bar()){

        }
        for(int i = 0;i<4;i++);
        for(int i = 0;i<4;i++)
        {

        }
        if(bar());
        if(bar()){

        }
        if(bar()){
            return;
        }
        else
        {

        }
        if(bar()){
            return;
        }
        else;

    }

    private boolean bar()
    {
        return true;
    }
}
