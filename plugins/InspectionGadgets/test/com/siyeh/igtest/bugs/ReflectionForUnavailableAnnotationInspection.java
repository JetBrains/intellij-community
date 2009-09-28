package com.siyeh.igtest.bugs;

import java.lang.annotation.Retention;

public class
        ReflectionForUnavailableAnnotationInspection {
    public void foo() {
        getClass().getAnnotation(Retention.class);
        getClass().getAnnotation(UnretainedAnnotation.class);
        getClass().getAnnotation(SourceAnnotation.class);
        getClass().isAnnotationPresent(Retention.class);
        getClass().isAnnotationPresent(UnretainedAnnotation.class);
        getClass().isAnnotationPresent(SourceAnnotation.class);
    }
}
