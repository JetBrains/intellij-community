package com.siyeh.ipp.psiutils;

public class ClassUtil{
    private ClassUtil(){
        super();
    }

    public static boolean classExists(String className){
        final Class<?> aClass;
        try{
            aClass = Class.forName(className);
        } catch(ClassNotFoundException ignore){
            return false;
        }
        return aClass != null;
    }
}
