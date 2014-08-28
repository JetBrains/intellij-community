
package com.vladium.utils.timing;

// ----------------------------------------------------------------------------
/**
 * This non-instantiable non-extendible class acts as a Factory for {@link ITimer}
 * implementations.
 * 
 * @author (C) <a href="mailto:vroubtsov@illinoisalumni.org">Vlad Roubtsov</a>, 2002
 */
public abstract class TimerFactory
{
    // public: ................................................................

	private static final String HRTIMER_LIB = "hrtlib";

    /**
     * Creates a new instance of {@link ITimer} which is returned in 'ready'
     * state. If the JNI-based/high-resolution implementation is not available
     * this will return an instance of <code>JavaSystemTimer</code>, so this
     * method is guaranteed not to fail.
     * 
     * @return ITimer a new timer instance in 'ready' state [never null]      */
	
	public static void initialize(String path) {

		UnsatisfiedLinkError exception = null;
		
		try {
			System.loadLibrary (HRTIMER_LIB);
		} catch (UnsatisfiedLinkError e) {
			if(path != null) {
				try {
					System.load(path);
				} catch (UnsatisfiedLinkError ex) {
					exception = ex;
				}
			} else {
				exception = e;
			}
		}

		if(exception != null) {
			System.out.println ("native lib '" + HRTIMER_LIB
					+ "' not found in 'java.library.path': "
					+ System.getProperty ("java.library.path")
					+path==null?"":(" or in "+path));

			throw exception; // re-throw
		}
	}
	
    public static ITimer newTimer ()
    {
//        try
//        {
            return new HRTimer ();
//        }
//        catch (Throwable t)
//        {
//            return new JavaSystemTimer ();
//        }
    }
    
    // protected: .............................................................

    // package: ...............................................................
    
    // private: ...............................................................
    
    private TimerFactory () {} // prevent subclassing
    
} // end of class
// ----------------------------------------------------------------------------
