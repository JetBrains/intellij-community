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
 * The request for a command.
 * Always a response is expected.
 *
 * @author  Thomas Singer
 */
public final class CommandRequest extends ResponseExpectingRequest {

        // Constants ==============================================================

        public static final CommandRequest ADD = new CommandRequest("add");
        public static final CommandRequest ADMIN = new CommandRequest("admin");
        public static final CommandRequest ANNOTATE = new CommandRequest("annotate");
        public static final CommandRequest CHECKOUT = new CommandRequest("co");
        public static final CommandRequest EXPORT = new CommandRequest("export");
        public static final CommandRequest COMMIT = new CommandRequest("ci");
        public static final CommandRequest EDITORS = new CommandRequest("editors");
        public static final CommandRequest IMPORT = new CommandRequest("import");
        public static final CommandRequest LOG = new CommandRequest("log");
        public static final CommandRequest RLOG = new CommandRequest("rlog");
        public static final CommandRequest NOOP = new CommandRequest("noop");
        public static final CommandRequest REMOVE = new CommandRequest("remove");
        public static final CommandRequest STATUS = new CommandRequest("status");
        public static final CommandRequest TAG = new CommandRequest("tag");
        public static final CommandRequest RTAG = new CommandRequest("rtag");
        public static final CommandRequest UPDATE = new CommandRequest("update");
        public static final CommandRequest WATCH_ADD = new CommandRequest("watch-add");
        public static final CommandRequest WATCH_ON = new CommandRequest("watch-on");
        public static final CommandRequest WATCH_OFF = new CommandRequest("watch-off");
        public static final CommandRequest WATCH_REMOVE = new CommandRequest("watch-remove");
        public static final CommandRequest WATCHERS = new CommandRequest("watchers");

        // Setup ==================================================================

        private CommandRequest(@NonNls String requestString) {
                super(requestString);
        }
}
