
package com.vladium.utils.timing;

// ----------------------------------------------------------------------------
/**
 * A package-private collection of constants used by {@link ITimer} implementations
 * in <code>HRTimer</code> and <code>JavaSystemTimer</code> classes.
 *
 * @author (C) <a href="mailto:vroubtsov@illinoisalumni.org">Vlad Roubtsov</a>, 2002
 */
interface ITimerConstants
{
    // public: ................................................................
    
    /**
     * Conditional compilation flag to enable/disable state checking in timer
     * implementations. Just about the only reason you might want to disable
     * this is to reduce the timer overhead, but in practice the gain is very
     * small.      */
    static final boolean DO_STATE_CHECKS = true;
    
    /**
     * Timer state enumeration.      */
    static final int STATE_READY = 0, STATE_STARTED = 1, STATE_STOPPED = 2;
    
    /**
     * User-friendly timer state names indexed by their state values.     */
    static final String [] STATE_NAMES = {"READY", "STARTED", "STOPPED"};

} // end of interface
// ----------------------------------------------------------------------------
