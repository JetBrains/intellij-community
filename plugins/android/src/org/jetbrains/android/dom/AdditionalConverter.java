package org.jetbrains.android.dom;

import com.intellij.util.xml.ResolvingConverter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Eugene.Kudelevsky
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface AdditionalConverter {
  Class<? extends ResolvingConverter> value();
}
