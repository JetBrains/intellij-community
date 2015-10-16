package com.siyeh.igtest.security;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class JDBCExecuteWithNonConstantString
{
    public JDBCExecuteWithNonConstantString()
    {
    }

    public void foo() throws IOException, SQLException
    {
        Statement statement = null;
        statement.executeQuery("foo" );
        statement.<warning descr="Call to 'Statement.executeQuery()' with non-constant argument">executeQuery</warning>("foo" + bar());
        statement.<warning descr="Call to 'Statement.addBatch()' with non-constant argument">addBatch</warning>("foo" + bar());
    }

    private String bar() {
        return "bar";
    }
}