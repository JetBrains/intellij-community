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
package org.netbeans.lib.cvsclient.command;

import org.netbeans.lib.cvsclient.JavaCvsSrcBundle;

/**
 * This exception is thrown when a command is aborted during execution
 * @author  Robert Greig
 */
public final class CommandAbortedException extends CommandException {

        public CommandAbortedException() {
                super(JavaCvsSrcBundle.message("command.aborted.by.user.error.message"));
        }

}
