package com.siyeh.igtest.bugs;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ResultSetIndexZeroInspection {
    private static final int COLUMN_INDEX = 0;

    public void foo(ResultSet resultSet) throws SQLException {
        resultSet.getInt(0);
        resultSet.getInt(COLUMN_INDEX);
        resultSet.getInt(3);
    }
}
