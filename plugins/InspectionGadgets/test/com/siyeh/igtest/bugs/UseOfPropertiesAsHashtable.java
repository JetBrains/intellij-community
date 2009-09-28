package com.siyeh.igtest.bugs;

import java.util.Properties;

public class UseOfPropertiesAsHashtable {
    public static void main(String[] args) {
        Properties properties = new Properties();
        properties.put("foo", "bar");
        properties.putAll(null);
        properties.get("foo");
    }
}
