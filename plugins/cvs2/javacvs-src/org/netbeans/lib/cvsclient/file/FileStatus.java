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
package org.netbeans.lib.cvsclient.file;

import org.netbeans.lib.cvsclient.JavaCvsSrcBundle;

/**
 * This class provides constants for file statuses.
 * @author  Robert Greig
 */
public final class FileStatus {
        /**
         * Returns the corresponding FileStatus constant for the specified String.
         */
        public static FileStatus getStatusForString(String statusString) {
                if (statusString == null) {
                        return null;
                }

                if (statusString.equals(ADDED.toString())) {
                        return ADDED;
                }
                if (statusString.equals(REMOVED.toString())) {
                        return REMOVED;
                }
                if (statusString.equals(MODIFIED.toString())) {
                        return MODIFIED;
                }
                if (statusString.equals(UP_TO_DATE.toString())) {
                        return UP_TO_DATE;
                }
                if (statusString.equals(NEEDS_CHECKOUT.toString())) {
                        return NEEDS_CHECKOUT;
                }
                if (statusString.equals(NEEDS_MERGE.toString())) {
                        return NEEDS_MERGE;
                }
                if (statusString.equals(NEEDS_PATCH.toString())) {
                        return NEEDS_PATCH;
                }
                if (statusString.equals(HAS_CONFLICTS.toString())) {
                        return HAS_CONFLICTS;
                }
                if (statusString.equals(UNKNOWN.toString())) {
                        return UNKNOWN;
                }
                return null;
        }

        /**
         * The Added status, i.e. the file has been added to the repository
         * but not committed yet.
         */
        public static final FileStatus ADDED = new FileStatus(JavaCvsSrcBundle.message("file.status.locally.added"));

        /**
         * The Removed status, i.e. the file has been removed from the repository
         * but not committed yet
         */
        public static final FileStatus REMOVED = new FileStatus(JavaCvsSrcBundle.message("file.status.locally.removed"));

        /**
         * The locally modified status, i.e. the file has been modified locally
         * and is out of sync with the repository
         */
        public static final FileStatus MODIFIED = new FileStatus(JavaCvsSrcBundle.message("file.status.locally.modified"));

        /**
         * The up-to-date status, i.e. the file is in sync with the repository
         */
        public static final FileStatus UP_TO_DATE = new FileStatus(JavaCvsSrcBundle.message("file.status.up.to.date"));

        /**
         * The "needs checkout" status, i.e. the file is out of sync with the
         * repository and needs to be updated
         */
        public static final FileStatus NEEDS_CHECKOUT = new FileStatus(JavaCvsSrcBundle.message("file.status.needs.checkout"));

        /**
         * The "needs patch" status, i.e. the file is out of sync with the
         * repository and needs to be patched
         */
        public static final FileStatus NEEDS_PATCH = new FileStatus(JavaCvsSrcBundle.message("file.status.needs.patch"));

        /**
         * The "needs merge" status, i.e. the file is locally modified and
         * the file in the repository has been modified too
         */
        public static final FileStatus NEEDS_MERGE = new FileStatus(JavaCvsSrcBundle.message("file.status.needs.merge"));

        /**
         * The "conflicts" status, i.e. the file has been merged and now
         * has conflicts that need resolved before it can be checked-in
         */
        public static final FileStatus HAS_CONFLICTS = new FileStatus(JavaCvsSrcBundle.message("file.status.file.had.conflicts.on.merge"));

        /**
         * The unknown status, i.e. the file is not known to the CVS repository.
         */
        public static final FileStatus UNKNOWN = new FileStatus(JavaCvsSrcBundle.message("file.status.unknown"));

        private final String statusString;

        /**
         * Do not construct a FileStatus object youself, but use one of the static
         * constants.
         */
        private FileStatus(String statusString) {
                this.statusString = statusString;
        }

        /**
         * Returns the String representation for thiz.
         */
        public String toString() {
                return statusString;
        }
}