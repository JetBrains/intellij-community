package com.siyeh.igtest.annotation;

import java.lang.annotation.*;

@Target(value= ElementType.PACKAGE)
@Retention(value= RetentionPolicy.RUNTIME)
@Documented
public @interface TestAnnotation {

}