/*****************************************************************************
 * Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License Version
 * 1.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is available at http://www.sun.com/

 * The Original Code is the CVS Client Library.
 * The Initial Developer of the Original Code is Robert Greig.
 * Portions created by Robert Greig are Copyright (C) 2000.
 * All Rights Reserved.

 * Contributor(s): Robert Greig.
 *****************************************************************************/
package org.netbeans.lib.cvsclient.event;

import org.jetbrains.annotations.NonNls;

/**
 * An event sent from the server to indicate that a message should be
 * displayed to the user
 * @author  Robert Greig
 */
public final class TaggedMessageParser {

        // Fields =================================================================

        private final StringBuffer buffer = new StringBuffer();
  @NonNls private static final String NEWLINE_MESSAGE = "newline";

  // Accessing ==============================================================

        public String getString() {
                if (buffer.length() == 0) {
                        return null;
                }

                final String line = buffer.toString();
                buffer.setLength(0);
                return line;
        }

        // Actions ================================================================

        public String parseTaggedMessage(String taggedMessage) {
                if (taggedMessage.charAt(0) == '+' || taggedMessage.charAt(0) == '-') {
                        return null;
                }

                if (taggedMessage.equals(NEWLINE_MESSAGE)) {
                        return getString();
                }

                final int index = taggedMessage.indexOf(' ');
                if (index > 0) {
                        buffer.append(taggedMessage.substring(index + 1));
                }
                return null;
        }
}