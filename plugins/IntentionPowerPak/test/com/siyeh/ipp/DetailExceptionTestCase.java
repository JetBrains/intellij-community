package com.siyeh.ipp;

import java.sql.DriverManager;
import java.sql.Connection;

public class DetailExceptionTestCase{
    public void foo()
    {
        Connection conn;
        try{
            Class.forName("logon.name");
            conn = DriverManager
                    .getConnection("jdbc:mysql://localhost/test", "root", "");
        } catch(Exception e){

        }
    }
}
