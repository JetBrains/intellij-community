package com.siyeh.igtest.security;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public class JDBCPrepareStatementWithNonConstantStringInspection
{
    public JDBCPrepareStatementWithNonConstantStringInspection()
    {
    }

    public void foo() throws IOException, SQLException
    {
        Connection connection = null;
        connection.prepareStatement("foo");
        connection.prepareStatement("foo" + bar());
        connection.prepareCall("foo" + bar());
    }

    private String bar() {
        return "bar";
    }
}