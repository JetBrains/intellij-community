package com.siyeh.igtest.exceptionHandling;

import java.io.FileNotFoundException;
import java.io.EOFException;
import java.io.IOException;

public class TooBroadCatchBlock{
    public void foo(){
        try{
            if(bar()){
                throw new FileNotFoundException();
            } else{
                throw new EOFException();
            }
        } catch(FileNotFoundException e){
            e.printStackTrace();
        } catch(IOException e){
            e.printStackTrace();
        }
    }

    private boolean bar(){
        return false;
    }
}
