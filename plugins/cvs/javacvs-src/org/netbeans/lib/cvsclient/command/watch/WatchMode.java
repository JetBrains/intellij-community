/*
 *                 Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License
 * Version 1.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://www.sun.com/
 *
 * The Original Code is NetBeans. The Initial Developer of the Original
 * Code is Sun Microsystems, Inc. Portions Copyright 1997-2001 Sun
 * Microsystems, Inc. All Rights Reserved.
 */
package org.netbeans.lib.cvsclient.command.watch;

import org.netbeans.lib.cvsclient.request.CommandRequest;
import org.netbeans.lib.cvsclient.JavaCvsSrcBundle;

/**
 * @author  Thomas Singer
 */
public final class WatchMode {

        // Constants ==============================================================

        /**
         * This is the WatchMode that enables watching.
         */
        public static final WatchMode ON = new WatchMode(JavaCvsSrcBundle.message("watch.mode.on"),
                                                         CommandRequest.WATCH_ON,
                                                         false);

        /**
         * This is the WatchMode that disables watching.
         */
        public static final WatchMode OFF = new WatchMode(JavaCvsSrcBundle.message("watch.mode.off"),
                                                          CommandRequest.WATCH_OFF,
                                                          false);

        /**
         * This is the WatchMode that adds watching for the specified Watch.
         */
        public static final WatchMode ADD = new WatchMode(JavaCvsSrcBundle.message("watch.mode.add"),
                                                          CommandRequest.WATCH_ADD,
                                                          true);

        /**
         * This is the WatchMode that removes watching for the specified Watch.
         */
        public static final WatchMode REMOVE = new WatchMode(JavaCvsSrcBundle.message("watch.mode.remove"),
                                                             CommandRequest.WATCH_REMOVE,
                                                             true);

        // Fields =================================================================

        private final String name;
        private final CommandRequest command;
        private final boolean watchOptionAllowed;

        // Setup ==================================================================

        private WatchMode(String name, CommandRequest command, boolean watchOptionAllowed) {
                this.name = name;
                this.command = command;
                this.watchOptionAllowed = watchOptionAllowed;
        }

        // Implemented ============================================================

        /**
         * Returns the name of this WatchMode ("on", "off", "add", "remove").
         */
        public String toString() {
                return name;
        }

        // Accessing ==============================================================

        /**
         * Returns the CommandRequest that is used when executing the WatchCommand
         * with this WatchMode.
         */
        public CommandRequest getCommand() {
                return command;
        }

        /**
         * Indicated, whether a non-null watch-option is allowed in the WatchCommand.
         */
        public boolean isWatchOptionAllowed() {
                return watchOptionAllowed;
        }
}
