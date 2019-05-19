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
package org.netbeans.lib.cvsclient.request;

import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.util.BugLog;

/**
 * The global options request.
 * Sends global switch to the server.
 *
 * @author  Milos Kleint
 */
public final class GlobalOptionRequest extends AbstractRequest {

        // Constants ==============================================================

        @NonNls public static final String REQUEST = "Global_option";

        // Fields =================================================================

        private final String option;

        // Setup ==================================================================

        public GlobalOptionRequest(@NonNls String option) {
                BugLog.getInstance().assertNotNull(option);

                this.option = option;
        }

        // Implemented ============================================================

        /**
         * Get the request String that will be passed to the server.
         * @return the request String
         */
        @Override
        public String getRequestString() {
                return REQUEST + ' ' + option + '\n';
        }
}