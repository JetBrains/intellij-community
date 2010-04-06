package com.trilead.ssh2;

/**
 * An interface which needs to be implemented if you
 * want to capture debugging messages.
 * 
 * @see Connection#enableDebugging(boolean, DebugLogger)
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: DebugLogger.java,v 1.1 2008/03/03 07:01:36 cplattne Exp $
 */
public interface DebugLogger
{

/**
 * Log a debug message.
 * 
 * @param level 0-99, 99 is a the most verbose level
 * @param className the class that generated the message
 * @param message the debug message
 */
	public void log(int level, String className, String message);	
}
