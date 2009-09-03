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
package org.netbeans.lib.cvsclient.request;

import org.netbeans.lib.cvsclient.file.FileDetails;
import org.jetbrains.annotations.NonNls;

/**
 * The superclass of all requests made to the CVS server
 * @author  Robert Greig
 */
public interface IRequest {

        /**
         * Get the request String that will be passed to the server
         * @return the request String
         */
        @NonNls String getRequestString();

        /**
         * If a file transmission is required, get the file object representing
         * the file to transmit after the request string. The default
         * implementation returns null, indicating no file is to be transmitted
         * @return the file object, if one should be transmitted, or null if
         * no file object is to be transmitted.
         */
        FileDetails getFileForTransmission();
}