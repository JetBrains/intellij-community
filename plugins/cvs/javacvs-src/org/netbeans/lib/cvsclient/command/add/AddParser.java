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
package org.netbeans.lib.cvsclient.command.add;

import org.netbeans.lib.cvsclient.command.DefaultEntryParser;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;

/**
 * @author  Thomas Singer
 */
final class AddParser extends DefaultEntryParser {

	// Setup ==================================================================

	AddParser(IEventSender eventManager, ICvsFileSystem cvsFileSystem) {
		super(eventManager, cvsFileSystem);
	}
}
