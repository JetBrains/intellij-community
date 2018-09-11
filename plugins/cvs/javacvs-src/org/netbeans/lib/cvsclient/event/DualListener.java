package org.netbeans.lib.cvsclient.event;

import org.netbeans.lib.cvsclient.util.BugLog;

/**
 * @author Thomas Singer
 */
public class DualListener
        implements ICvsListener {

	// Fields =================================================================

    private final ICvsListener parser1;
	private final ICvsListener parser2;

	// Setup ==================================================================

	public DualListener(ICvsListener parser1, ICvsListener parser2) {
		BugLog.getInstance().assertNotNull(parser1);
		BugLog.getInstance().assertNotNull(parser2);

		this.parser1 = parser1;
		this.parser2 = parser2;
	}

	// Implemented ============================================================

	@Override
        public void registerListeners(ICvsListenerRegistry listenerRegistry) {
        parser1.registerListeners(listenerRegistry);
		parser2.registerListeners(listenerRegistry);
	}

	@Override
        public void unregisterListeners(ICvsListenerRegistry listenerRegistry) {
        parser2.unregisterListeners(listenerRegistry);
        parser1.unregisterListeners(listenerRegistry);
	}
}
