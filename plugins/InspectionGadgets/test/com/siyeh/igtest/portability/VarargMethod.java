package com.siyeh.igtest.portability;

public class VarargMethod {

    public static void main(String[] args) {

    }

    public int sum(int... var)
    {
        int total = 0;
        for (int i = 0; i < var.length; i++) {
            total+= var[i];
        }
        return total;
    }

}
