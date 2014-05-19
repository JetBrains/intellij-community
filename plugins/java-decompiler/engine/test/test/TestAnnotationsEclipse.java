package test;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class TestAnnotationsEclipse {

	public String a;

	@Retention(RetentionPolicy.CLASS)
	@interface MyAnnotation {}

	public static void main(String[] args) {
	
		TestInner a = new TestAnnotationsEclipse().new TestInner();
		
		for(Constructor mt : a.getClass().getConstructors()) {

			Annotation[][] ann = mt.getParameterAnnotations();
		
			System.out.println(ann.length);
		}		
	}
	
	protected class TestInner {

		public TestInner() {}
		
		public TestInner(String param1, Object param2, @MyAnnotation boolean param3) {
			System.out.println(param1);
			System.out.println(param2);
			System.out.println(param3);
		}

		public void accessField() {
			System.out.println(TestAnnotationsEclipse.this.a);
		}
	}
}
