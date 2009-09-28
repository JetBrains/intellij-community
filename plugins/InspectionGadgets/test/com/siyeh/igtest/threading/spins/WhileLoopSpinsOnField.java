package com.siyeh.igtest.threading.spins;

class WhileLoopSpinsOnField
{
    private int quantity_;

    public static void main(final String[] args)
    {
        final WhileLoopSpinsOnField wlsof = new WhileLoopSpinsOnField();
        wlsof.countdown();
    }

    private WhileLoopSpinsOnField()
    {
        quantity_ = 10;
    }

    private void countdown()
    {
        while (quantity_ != 0)
        {
            System.out.println(quantity_);
            quantity_--;
        }
    }
}