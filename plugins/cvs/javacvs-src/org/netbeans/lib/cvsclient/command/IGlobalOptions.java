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

import java.util.Map;

/**
 * @author Thomas Singer
 * @version Apr 27, 2002
 */
public interface IGlobalOptions {

	boolean isCheckedOutFilesReadOnly();

	boolean isDoNoChanges();

	boolean isNoHistoryLogging();

	boolean isUseGzip();

	boolean isSomeQuiet();

	Map<String, String> getEnvVariables();
}
