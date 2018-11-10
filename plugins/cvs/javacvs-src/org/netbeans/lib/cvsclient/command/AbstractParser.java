/*
 *                 Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License
 * Version 1.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://www.sun.com/
 *
 * The Original Code is NetBeans. The Initial Developer of the Original
 * Code is Sun Microsystems, Inc. Portions Copyright 1997-2000 Sun
 * Microsystems, Inc. All Rights Reserved.
 */
package org.netbeans.lib.cvsclient.command;

import org.netbeans.lib.cvsclient.event.ICvsListener;
import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.ITerminationListener;

/**
 * @author  Thomas Singer
 */
public abstract class AbstractParser
        implements ICvsListener, ITerminationListener {

	// Abstract ===============================================================

	protected abstract void outputDone();

	// Implemented ============================================================

	@Override
        public void registerListeners(ICvsListenerRegistry listenerRegistry) {
		listenerRegistry.addTerminationListener(this);
	}

	@Override
        public void unregisterListeners(ICvsListenerRegistry listenerRegistry) {
		listenerRegistry.removeTerminationListener(this);
	}

	@Override
        public void commandTerminated(boolean error) {
		outputDone();
	}
}
