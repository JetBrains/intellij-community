/*****************************************************************************
 * Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License Version
 * 1.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is available at http://www.sun.com/
 *
 * The Original Code is the CVS Client Library.
 * The Initial Developer of the Original Code is Robert Greig.
 * Portions created by Robert Greig are Copyright (C) 2000.
 * All Rights Reserved.
 *
 * Contributor(s): Robert Greig.
 *****************************************************************************/
package org.netbeans.lib.cvsclient;

import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.progress.sending.IRequestsProgressHandler;
import org.netbeans.lib.cvsclient.request.Requests;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;

/**
 * @author  Thomas Singer
 */
public interface IRequestProcessor {

	boolean processRequests(Requests requests, IRequestsProgressHandler communicationProgressHandler) throws CommandException,
                                                                                                                 AuthenticationException;
}
