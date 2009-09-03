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
package org.netbeans.lib.cvsclient.util;

import org.jetbrains.annotations.NonNls;

/**
 * @author  Thomas Singer
 */
public class BugLog {

        // Static =================================================================

        private static BugLog instance;

        public synchronized static BugLog getInstance() {
                if (instance == null) {
                        instance = new BugLog();
                }
                return instance;
        }

        public synchronized static void setInstance(BugLog instance) {
                BugLog.instance = instance;
        }

        // Setup ==================================================================

        public BugLog() {
        }

        // Actions ================================================================

        public void showException(Exception ex) {
                ex.printStackTrace();
        }

        public void assertTrue(boolean value,@NonNls  String message) {
                if (value) {
                        return;
                }

                throw new BugException(message);
        }

        public void assertNotNull(Object obj) {
                if (obj != null) {
                        return;
                }

                throw new BugException("Value must not be null!");
        }

        // Inner classes ==========================================================

        public static final class BugException extends RuntimeException {
                private BugException(@NonNls String message) {
                        super(message);
                }
        }
}
