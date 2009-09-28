package com.siyeh.igtest.portability;

import java.sql.Driver;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.DriverPropertyInfo;
import java.util.Properties;


public class UseOfJDBCDriverInspection implements Driver{
    UseOfJDBCDriverInspection foo = new UseOfJDBCDriverInspection();

    public int getMajorVersion() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public int getMinorVersion() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean jdbcCompliant() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean acceptsURL(String string) throws SQLException {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Connection connect(String string, Properties properties) throws SQLException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public DriverPropertyInfo[] getPropertyInfo(String string, Properties properties) throws SQLException {
        return new DriverPropertyInfo[0];  //To change body of implemented methods use File | Settings | File Templates.
    }
}
