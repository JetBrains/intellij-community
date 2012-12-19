package com.siyeh.igtest.errorhandling.caught_exception_immediately_rethrown;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;

public class CaughtExceptionImmediatelyRethrown {

    void foo() throws FileNotFoundException {
        try {
            new FileInputStream(new File(""));
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            throw e;
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    void conflict() throws FileNotFoundException {
        try {
            int i = 0;
            new FileInputStream(new File(""));
        } catch (FileNotFoundException e) {
            throw e;
        }
        int i = 10;
    }

	void notImmediately(boolean notsure) throws InterruptedException {
		try {
			Thread.sleep(10000L);
		} catch (InterruptedException ex) {
			if (notsure) throw ex;
		}
	}

	protected static Method getActionMethod(Class<?> actionClass, String methodName)
			throws NoSuchMethodException {
		Method method;
		try {
			method = actionClass.getMethod(methodName);
		} catch (NoSuchMethodException e) {
			// hmm -- OK, try doXxx instead
			try {
				final String altMethodName = "do" + methodName.substring(0, 1).toUpperCase() + methodName.substring(1);
				method = actionClass.getMethod(altMethodName);
			} catch (NoSuchMethodException e1) {
				// throw the original one
				throw e;
			}
		}
		return method;
	}

    public void test() throws IOException {
        try {
            // some code here
        } catch(IllegalStateException | UnsupportedOperationException e) {
            throw e;
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }
}