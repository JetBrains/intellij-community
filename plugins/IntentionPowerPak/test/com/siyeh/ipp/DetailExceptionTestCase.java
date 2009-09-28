package com.siyeh.ipp;

import java.sql.DriverManager;
import java.sql.Connection;

public class DetailExceptionTestCase{
    public void foo()
    {
        Connection conn;
        try{
            Class.forName("logon.name");
            DriverManager
                    .getConnection("jdbc:mysql://localhost/test", "root", "");
        } catch(ClassNotFoundException e){

        } catch(Exception e){

        }
    }
}
