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
package org.netbeans.lib.cvsclient.command;

import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.JavaCvsSrcBundle;

/**
 * @author  Thomas Singer
 * @version Dec 2, 2001
 */
public final class Watch {

        // Constants ==============================================================

        public static final Watch EDIT = new Watch(JavaCvsSrcBundle.message("watch.name.edit"), "E", // NOI18N
                                                   new String[]{"edit"}); // NOI18N

        public static final Watch UNEDIT = new Watch(JavaCvsSrcBundle.message("watch.name.unedit"), "U", // NOI18N
                                                     new String[]{"unedit"}); // NOI18N

        public static final Watch COMMIT = new Watch(JavaCvsSrcBundle.message("watch.name.commit"), "C", // NOI18N
                                                     new String[]{"commit"}); // NOI18N

        public static final Watch ALL = new Watch(JavaCvsSrcBundle.message("watch.name.all"), "EUC", // NOI18N
                                                  new String[]{"edit", "unedit", "commit"}); // NOI18N

        public static final Watch TALL = new Watch(JavaCvsSrcBundle.message("watch.name.all"), "EUC", // NOI18N
                                                   new String[]{"tedit", "tunedit", "tcommit"}); // NOI18N

        public static final Watch NONE = new Watch(JavaCvsSrcBundle.message("watch.name.none"), "", // NOI18N
                                                   ArrayUtilRt.EMPTY_STRING_ARRAY);

        // Static =================================================================

        /**
         * Returns the temporary watch value used in the Notify request.
         */
        public static String getWatchString(Watch watch) {
                if (watch == null) {
                        return NONE.getValue();
                }
                return watch.getValue();
        }

        // Fields =================================================================

        private final String name;
        private final String value;
        private final String[] arguments;

        // Setup ==================================================================

        private Watch(String name, @NonNls String value, @NonNls String[] arguments) {
                this.name = name;
                this.value = value;
                this.arguments = arguments;
        }

        // Implemented ============================================================

        public String toString() {
                return name;
        }

        // Accessing ==============================================================

        public String[] getArguments() {
                return arguments;
        }

        private String getValue() {
                return value;
        }
}
