package com.siyeh.igtest.performance;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;

public class JavaLangReflectInspection
{
    public JavaLangReflectInspection()
    {
    }

    public void fooBar() throws IOException
    {
        final Class thisClass = getClass();
        final Field[] fields = thisClass.getFields();
        for(int i = 0; i < fields.length; i++)
        {
            final Field field = fields[i];
            System.out.println("field = " + field);
        }
        final Constructor[] constructors = thisClass.getConstructors();
    }
}