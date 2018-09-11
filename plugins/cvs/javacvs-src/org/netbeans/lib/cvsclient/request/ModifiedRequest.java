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

import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.file.FileDetails;
import org.netbeans.lib.cvsclient.file.FileObject;

/**
 * Sends the server a copy of a locally modified file.
 * @author  Robert Greig
 */
public final class ModifiedRequest extends AbstractFileStateRequest {

        // Fields =================================================================

        private final FileDetails fileDetails;
        private final boolean writable;

        // Setup ==================================================================

        public ModifiedRequest(FileObject fileObject, boolean isBinary, boolean writable) {
                super(fileObject);

                this.fileDetails = new FileDetails(fileObject, isBinary);
                this.writable = writable;
        }

        // Implemented ============================================================

        /**
         * Get the request String that will be passed to the server
         * @return the request String
         */
        @Override
        public String getRequestString() {
                @NonNls final StringBuilder request = new StringBuilder();
                request.append("Modified ");
                request.append(getFileName());
                request.append('\n');
                if (writable) {
                        request.append("u=rw,g=r,o=r");
                }
                else {
                        request.append("u=r,g=r,o=r");
                }
                request.append('\n');
                return request.toString();
        }

        /**
         * If a file transmission is required, get the file object representing
         * the file to transmit after the request string. The default
         * implementation returns null, indicating no file is to be transmitted
         * @return the file details object, if one should be transmitted, or null
         * if no file object is to be transmitted.
         */
        @Override
        public FileDetails getFileForTransmission() {
                return fileDetails;
        }
}
