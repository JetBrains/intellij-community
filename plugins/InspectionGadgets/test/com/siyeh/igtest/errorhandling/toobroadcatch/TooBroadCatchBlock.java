package com.siyeh.igtest.errorhandling.toobroadcatch;

import java.io.FileNotFoundException;
import java.io.EOFException;
import java.io.IOException;
import java.net.URL;

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

    void foos() {
        try {
            new URL(null);
            throw new NullPointerException();
        } catch (IOException e) {

        } catch (RuntimeException e) {

        }
    }
}
