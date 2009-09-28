package com.siyeh.igtest.verbose;

public class UnnecessaryBlockStatementInspection {
    public static void main(String[] args) {
        {
             System.out.println("3");
        }
        {
            int a;
        }
        {
            int b;
        }
        {
            {
            int a;
            }
        }
    }
}
