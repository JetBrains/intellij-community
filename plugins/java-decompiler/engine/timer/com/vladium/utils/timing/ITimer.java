
package com.vladium.utils.timing;

// ----------------------------------------------------------------------------
/**
 * A simple interface for measuring time intervals. An instance of this goes
 * through the following lifecycle states:
 * <DL>
 *  <DT> <EM>ready</EM>
 *      <DD> timer is ready to start a new measurement
 *  <DT> <EM>started</EM>
 *      <DD> timer has recorded the starting time interval point
 *  <DT> <EM>stopped</EM>
 *      <DD> timer has recorded the ending time interval point
 * </DL>
 * See individual methods for details.<P>
 * 
 * If this library has been compiled with {@link ITimerConstants#DO_STATE_CHECKS}
 * set to 'true' the implementation will enforce this lifecycle model and throw
 * IllegalStateException when it is violated.
 * 
 * @author (C) <a href="mailto:vroubtsov@illinoisalumni.org">Vlad Roubtsov</a>, 2002
 */
public interface ITimer
{
    // public: ................................................................
    
    /**
     * Starts a new time interval and advances this timer instance to 'started'
     * state. This method can be called from 'ready' state only.     */
    void start ();
    
    /**
     * Terminates the current time interval and advances this timer instance to
     * 'stopped' state. Interval duration will be available via
     * {@link #getDuration()} method. This method can be called from 'started'
     * state only.      */
    void stop ();
    
    /**
     * Returns the duration of the time interval that elapsed between the last
     * calls to {@link #start()} and {@link #stop()}. This method can be called
     * any number of times from 'stopped' state and will return the same value
     * each time.<P>
     *      * @return interval duration in milliseconds      */
    double getDuration ();
    
    /**
     * This method can be called from any state and will reset this timer
     * instance back to 'ready' state.      */
    void reset ();

} // end of interface
// ----------------------------------------------------------------------------
