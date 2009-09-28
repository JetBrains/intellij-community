package com.siyeh.igtest.bugs;

import java.math.BigDecimal;

public class BigDecimalEqualsInspection {
    public void foo()
    {
        final BigDecimal foo = new BigDecimal(3);
        final BigDecimal bar = new BigDecimal(3);
        foo.equals(bar);
        if(foo.equals(bar))
        {
            return;
        }
    }
}
