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

import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.IMessageListener;
import org.netbeans.lib.cvsclient.event.TaggedMessageParser;

/**
 * @author  Thomas Singer
 */
public abstract class AbstractMessageParser extends AbstractParser
        implements IMessageListener {

	// Abstract ===============================================================

	protected abstract void parseLine(String line, boolean error);

	// Fields =================================================================

	private final TaggedMessageParser taggedMessageParser = new TaggedMessageParser();

	// Implemented ============================================================

	@Override
        public void registerListeners(ICvsListenerRegistry listenerRegistry) {
		listenerRegistry.addMessageListener(this);
		super.registerListeners(listenerRegistry);
	}

	@Override
        public void unregisterListeners(ICvsListenerRegistry listenerRegistry) {
		super.unregisterListeners(listenerRegistry);
		listenerRegistry.removeMessageListener(this);
	}

	@Override
        public final void messageSent(String message, final byte[] byteMessage, boolean error, boolean tagged) {
		if (tagged) {
			final String parsedMessage = taggedMessageParser.parseTaggedMessage(message);
			if (parsedMessage != null) {
				parseLine(parsedMessage, false);
			}
		}
		else {
			final String taggedLine = taggedMessageParser.getString();
			if (taggedLine != null) {
				parseLine(taggedLine, false);
			}
			parseLine(message, error);
		}
	}

	@Override
        public final void commandTerminated(boolean error) {
		final String taggedLine = taggedMessageParser.getString();
		if (taggedLine != null) {
			parseLine(taggedLine, false);
		}
		super.commandTerminated(error);
	}

  @Override
  public void binaryMessageSent(final byte[] bytes) {
  }
}
