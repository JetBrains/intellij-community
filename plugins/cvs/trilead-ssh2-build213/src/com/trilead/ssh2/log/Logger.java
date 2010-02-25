
package com.trilead.ssh2.log;

import com.trilead.ssh2.DebugLogger;

/**
 * Logger - a very simple logger, mainly used during development.
 * Is not based on log4j (to reduce external dependencies).
 * However, if needed, something like log4j could easily be
 * hooked in.
 * <p>
 * For speed reasons, the static variables are not protected
 * with semaphores. In other words, if you dynamicaly change the
 * logging settings, then some threads may still use the old setting.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: Logger.java,v 1.2 2008/03/03 07:01:36 cplattne Exp $
 */

public class Logger
{
	public static boolean enabled = false;
	public static DebugLogger logger = null;
	
	private String className;

	public final static Logger getLogger(Class x)
	{
		return new Logger(x);
	}

	public Logger(Class x)
	{
		this.className = x.getName();
	}

	public final boolean isEnabled()
	{
		return enabled;
	}

	public final void log(int level, String message)
	{
		if (!enabled)
			return;
		
		DebugLogger target = logger;
		
		if (target == null)
			return;
		
		target.log(level, className, message);
	}
}
