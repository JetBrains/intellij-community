/*****************************************************************************
 * Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License Version
 * 1.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is available at http://www.sun.com/
 *
 * The Original Code is the CVS Client Library.
 * The Initial Developer of the Original Code is Thomas Singer.
 * Portions created by Robert Greig are Copyright (C) 2001.
 * All Rights Reserved.
 *
 * Contributor(s): Thomas Singer.
 *****************************************************************************/
package org.netbeans.lib.cvsclient.command;

import org.netbeans.lib.cvsclient.JavaCvsSrcBundle;

/**
 * @author  Thomas Singer
 */
public final class CommandUtils {

        /**
         * Returns the directory relative to local path from the specified message.
         * This method returns null, if the specified message isn't a EXAM_DIR-
         * message.
         */
        public static String getExaminedDirectory(String message, String examDirPattern) {
                final int index = message.indexOf(examDirPattern);
                if (index < 0) {
                        return null;
                }

                return message.substring(index + examDirPattern.length());
        }

        public static String getMessageNotNull(String message) {
                if (message != null) {
                        message = message.trim();
                }
                if (message == null || message.length() == 0) {
                        message = JavaCvsSrcBundle.message("default.commit.message");
                }
                return message;
        }
}
