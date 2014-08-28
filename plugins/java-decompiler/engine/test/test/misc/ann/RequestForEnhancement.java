package test.misc.ann;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Describes the Request-For-Enhancement(RFE) that led
 * to the presence of the annotated API element.
 */

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequestForEnhancement {
	int    id();
	String synopsis();
	String engineer() default "[unassigned]"; 
	String date()    default "[unimplemented]"; 
	String[] arr();
	Class cl();
}
