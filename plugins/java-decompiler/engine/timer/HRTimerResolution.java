import java.text.DecimalFormat;

import com.vladium.utils.timing.ITimer;
import com.vladium.utils.timing.TimerFactory;

// ----------------------------------------------------------------------------
/**
 * A demo class to show off the higher resolution available from HRTimer class
 * and to investigate the resolution offered by Java "time-related"
 * methods other than System.currentTimeMillis().<P>
 * 
 * Make sure that hrtlib.dll JNI lib is in java.library.path or TimerFactory
 * will fall back to the Java system timer:
 * <PRE>
 *  >java -Djava.library.path=(dir containing hrtlib.dll) HRTimerResolution
 * </PRE>
 * 
 * @author (C) <a href="mailto:vroubtsov@illinoisalumni.org">Vlad Roubtsov</a>, 2002
 */
public class HRTimerResolution
{
    // public: ................................................................
    
    public static void main (final String [] args) throws Exception
    {
        final DecimalFormat format = new DecimalFormat ();
        format.setMinimumFractionDigits (3);
        format.setMaximumFractionDigits (3);
        
        // create an ITimer using the Factory class:
        final ITimer timer = TimerFactory.newTimer ();
        
        // JIT/hotspot warmup:
        for (int i = 0; i < 3000; ++ i)
        {
            timer.start ();
            timer.stop ();
            timer.getDuration ();
            timer.reset ();
        }
        
        final Object lock = new Object (); // this is used by monitor.wait() below
        
        for (int i = 0; i < 5; ++ i)
        {
            timer.start ();
           
            // uncomment various lines below to see the resolution
            // offered by other Java time-related methods; with all
            // lines commented out this loop reports time elapsed
            // between successive calls to t.start() and t.stop(), thus
            // providing an estimate for timer's raw resolution
            
            synchronized (lock) { lock.wait (1); }
            //Thread.currentThread ().sleep (1);
            //Thread.currentThread ().sleep (0, 500);
            //Thread.currentThread ().join (1);
    
            timer.stop ();

            System.out.println ("duration = "
                + format.format (timer.getDuration ()) + " ms");            
            timer.reset ();
        }
    } 

} // end of class
// ----------------------------------------------------------------------------
