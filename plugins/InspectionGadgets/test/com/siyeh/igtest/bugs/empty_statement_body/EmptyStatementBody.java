package com.siyeh.igtest.bugs.empty_statement_body;

public class EmptyStatementBody
{
    private void foo(int j)
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

        switch (j) {}
    }

    private boolean bar()
    {
        return true;
    }

    void comments(boolean b) {
      if (b); // comment
      while (b) {
        // comment
      }
      do {
        ; // comment
      } while (b);
    }
}
