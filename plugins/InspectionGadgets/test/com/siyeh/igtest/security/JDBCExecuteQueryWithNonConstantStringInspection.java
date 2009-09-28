package com.siyeh.igtest.security;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class JDBCExecuteQueryWithNonConstantStringInspection
{
    public JDBCExecuteQueryWithNonConstantStringInspection()
    {
    }

    public void foo() throws IOException, SQLException
    {
        Statement statement = null;
        statement.executeQuery("foo" );
        statement.executeQuery("foo" + bar());
        statement.addBatch("foo" + bar());
    }

    private String bar() {
        return "bar";
    }
}