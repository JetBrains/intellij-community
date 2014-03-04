// ----------------------------------------------------------------------------
/**
 * A simple class to see what the Java system timer resolution is on your
 * system.
 * 
 * @author (C) <a href="mailto:vroubtsov@illinoisalumni.org">Vlad Roubtsov</a>, 2002
 */
public class SystemTimerResolution
{
    // public: ................................................................
    
    public static void main (final String [] args)
    {
        // JIT/hotspot warmup:
        for (int r = 0; r < 3000; ++ r) System.currentTimeMillis ();
        
        long time = System.currentTimeMillis (), time_prev = time;
        
        for (int i = 0; i < 5; ++ i)
        {
            // busy wait until system time changes: 
            while (time == time_prev)
                time = System.currentTimeMillis ();
            
            System.out.println ("delta = " + (time - time_prev) + " ms");
            time_prev = time;
        }
    }

} // end of class
// ----------------------------------------------------------------------------
