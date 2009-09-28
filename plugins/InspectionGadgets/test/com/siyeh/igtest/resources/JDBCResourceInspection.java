package com.siyeh.igtest.resources;

import java.sql.*;

public class JDBCResourceInspection {
    private Driver driver;

    public void foo() throws SQLException {
        Connection connection = null;
        try {
            connection = driver.connect("foo", null);
        } finally {
            connection.close();
        }

    }

    public void foo2() throws SQLException {
        Connection connection = null;
        try {
            connection = driver.connect("foo", null);
        } finally {
        }
    }


    public void foo3() throws SQLException {
        Connection connection = null;
        try {
            connection = driver.connect("foo", null);
            connection.createStatement();
        } finally {
            connection.close();
        }

    }

    public void foo4() throws SQLException {
        Connection connection = null;
        Statement statement = null;
        try {
            connection = driver.connect("foo", null);
            statement = connection.createStatement();
        } finally {
            connection.close();
        }

    }

    public void foo5() throws SQLException {
        Connection connection = null;
        Statement statement = null;
        try {
            connection = driver.connect("foo", null);
            statement = connection.createStatement();
        } finally {
            statement.close();
            connection.close();
        }

    }

    public void foo6() throws SQLException {
        Connection connection = null;
        Statement statement = null;
        try {
            connection = driver.connect("foo", null);
            statement = connection.prepareStatement("foo");
        } finally {
            connection.close();
        }

    }

    public void foo7() throws SQLException {
        Connection connection = null;
        Statement statement = null;
        try {
            connection = driver.connect("foo", null);
            statement = connection.prepareStatement("foo");
        } finally {
            statement.close();
            connection.close();
        }

    }

    public void foo8() throws SQLException {
        Connection connection = null;
        Statement statement = null;
        try {
            connection = driver.connect("foo", null);
            statement = connection.prepareCall("foo");
        } finally {
            connection.close();
        }

    }

    public void foo9() throws SQLException {
        Connection connection = null;
        Statement statement = null;
        try {
            connection = driver.connect("foo", null);
            statement = connection.prepareCall("foo");
        } finally {
            statement.close();
            connection.close();
        }

    }

    public void foo10() throws SQLException {
        Connection connection = null;
        Statement statement = null;
        ResultSet resultSet = null;
        try {
            connection = driver.connect("foo", null);
            statement = connection.prepareCall("foo");
            resultSet = statement.executeQuery("foo");
        } finally {
            statement.close();
            connection.close();
        }

    }

    public void foo11() throws SQLException {
        Connection connection = null;
        Statement statement = null;
        ResultSet resultSet = null;
        try {
            connection = driver.connect("foo", null);
            statement = connection.prepareCall("foo");
            resultSet = statement.executeQuery("foo");
        } finally {
            resultSet.close();
            statement.close();
            connection.close();
        }

    }

}
