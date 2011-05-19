package com.siyeh.igtest.style.unnecessary_block_statement;

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

    void oldSwitcharoo() {
        switch (5) {
            case 1: {
                int x = 0;
                break;
            }
            case 2: {
                int x = 0;
                break;
            }
        }
    }

    void ifThenElse() {
        {
            int i = 0;
        }
        if (true) {
            int i = 0;
        }
        {
            int i = 0;
        }
    }
}
