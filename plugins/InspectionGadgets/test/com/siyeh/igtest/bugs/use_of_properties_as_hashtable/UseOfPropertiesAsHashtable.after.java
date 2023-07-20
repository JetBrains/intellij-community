package com.siyeh.igtest.bugs;

import java.util.Properties;

public class UseOfPropertiesAsHashtable {
    public static void main(String[] args) {
        Properties properties = new Properties();
        properties.setProperty("foo", "bar");
        properties.put("x", 1);
        properties.putAll(null);
        String value = (String) properties.getProperty("foo");
        Long l = (Long)properties.get("x");
    }
}
