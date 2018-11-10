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

import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.IRequestProcessor;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.progress.IProgressViewer;

/**
 * All commands must extend this class. A command is essentially a
 * collection of requests that make up what is logically known as a CVS
 * command (from a user's perspective). Commands correspond to operations the
 * user can perform with CVS, for example checkout a module or perform a
 * diff on two file versions.<br>
 * Commands are automatically added as CVS event listeners. They can act
 * on particular events and perhaps fire new events.
 *
 * @author  Robert Greig
 */
public abstract class Command {

        // Abstract ===============================================================

        public abstract boolean execute(IRequestProcessor requestProcessor, IEventSender eventManager, ICvsListenerRegistry listenerRegistry, IClientEnvironment clientEnvironment, IProgressViewer progressViewer)
          throws CommandException, AuthenticationException;

        /**
         * This method returns how the command would looklike when typed on the
         * command line.
         *
         * Each command is responsible for constructing this information.
         *
         * @return <command's name> [<parameters>] files/dirs. Example: checkout -p CvsCommand.java
         */
        @NonNls public abstract String getCvsCommandLine();

        // Fields =================================================================

        private final GlobalOptions globalOptions = new GlobalOptions();

        // Setup ==================================================================

        protected Command() {
                resetCvsCommand();
        }

        // Accessing ==============================================================

        public final GlobalOptions getGlobalOptions() {
                return globalOptions;
        }

        // Utils ==================================================================

        /**
         * Returns the trimmed version of the specified String s.
         * The returned String is null if the specified String is null or contains
         * only white spaces.
         */
        protected static String getTrimmedString(String s) {
                if (s == null) {
                        return null;
                }

                s = s.trim();
                if (s.isEmpty()) {
                        return null;
                }

                return s;
        }

        /**
         * Resets all switches in the command to the default behaviour.
         * After calling this method, the command should behave defaultly.
         */
        protected void resetCvsCommand() {
                if (globalOptions != null) {
                        globalOptions.reset();
                }
        }

        public void setUpdateByRevisionOrDate(String revision, final String date) {
                throw new UnsupportedOperationException();
        }
}
