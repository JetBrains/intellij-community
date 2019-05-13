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

/**
 * @author  Thomas Singer
 */
public final class GzipStreamRequest extends AbstractRequest {

        // Constants ==============================================================

        @NonNls public static final String REQUEST = "Gzip-stream";
        private static final int COMPRESSION_LEVEL = 6;

        // Implemented ============================================================

        @Override
        public String getRequestString() {
                return REQUEST + ' ' + COMPRESSION_LEVEL + '\n';
        }
}
