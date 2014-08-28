
package com.vladium.utils.timing;

// ----------------------------------------------------------------------------
/**
 * A package-private implementation of {@link ITimer} based around Java system
 * timer [<code>System.currentTimeMillis()</code> method]. It is used when
 * <code>HRTimer</code> implementation is unavailable.<P> 
 * 
 * {@link TimerFactory} acts as the Factory for this class.<P>
 * 
 * MT-safety: an instance of this class is safe to be used within the same
 * thread only.
 * 
 * @author (C) <a href="mailto:vroubtsov@illinoisalumni.org">Vlad Roubtsov</a>, 2002
 */
final class JavaSystemTimer implements ITimer, ITimerConstants 
{
    // public: ................................................................
    
    public void start ()
    {
        if (DO_STATE_CHECKS)
        {
            if (m_state != STATE_READY)
                throw new IllegalStateException (this + ": start() must be called from READY state, current state is " + STATE_NAMES [m_state]);
        }
        
        if (DO_STATE_CHECKS) m_state = STATE_STARTED;
        m_data = System.currentTimeMillis ();
    }
    
    public void stop ()
    {
        // latch stop time in a local var before doing anything else:
        final long data = System.currentTimeMillis ();
        
        if (DO_STATE_CHECKS)
        {
            if (m_state != STATE_STARTED)
                throw new IllegalStateException (this + ": stop() must be called from STARTED state, current state is " + STATE_NAMES [m_state]);
        }
        
        m_data = data - m_data;
        if (DO_STATE_CHECKS) m_state = STATE_STOPPED;
    }
    
    public double getDuration ()
    {
        if (DO_STATE_CHECKS)
        {
            if (m_state != STATE_STOPPED)
                throw new IllegalStateException (this + ": getDuration() must be called from STOPPED state, current state is " + STATE_NAMES [m_state]);
        }
        
        return m_data;
    }
    
    public void reset ()
    {
        if (DO_STATE_CHECKS) m_state = STATE_READY;
    }
    
    // protected: .............................................................

    // package: ...............................................................
        
    // private: ...............................................................
    
    private int m_state; // used to keep track of timer state
    private long m_data; // timing data

} // end of class
// ----------------------------------------------------------------------------
