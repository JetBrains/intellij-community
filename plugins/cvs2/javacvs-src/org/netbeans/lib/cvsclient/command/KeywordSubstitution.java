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

import org.jetbrains.annotations.NonNls;

/**
 * @author  Thomas Singer
 */
public final class KeywordSubstitution {

        // Constants ==============================================================

        public static final KeywordSubstitution BINARY = new KeywordSubstitution("b");
        public static final KeywordSubstitution KEYWORD_COMPRESSION = new KeywordSubstitution("k");
        public static final KeywordSubstitution KEYWORD_EXPANSION = new KeywordSubstitution("kv");
        public static final KeywordSubstitution KEYWORD_EXPANSION_LOCKER = new KeywordSubstitution("kvl");
        public static final KeywordSubstitution NO_SUBSTITUTION = new KeywordSubstitution("o");
        public static final KeywordSubstitution KEYWORD_REPLACEMENT = new KeywordSubstitution("v");

        // Static =================================================================

        public static KeywordSubstitution getValue(String string) {
                if (string == null) {
                        return null;
                }

                if (string.equals(BINARY.value)) {
                        return BINARY;
                }

                if (string.equals(KEYWORD_COMPRESSION.value)) {
                        return KEYWORD_COMPRESSION;
                }

                if (string.equals(KEYWORD_EXPANSION.value)) {
                        return KEYWORD_EXPANSION;
                }

                if (string.equals(KEYWORD_EXPANSION_LOCKER.value)) {
                        return KEYWORD_EXPANSION_LOCKER;
                }

                if (string.equals(NO_SUBSTITUTION.value)) {
                        return NO_SUBSTITUTION;
                }

                if (string.equals(KEYWORD_REPLACEMENT.value)) {
                        return KEYWORD_REPLACEMENT;
                }

                return null;
        }

        public static String toString(KeywordSubstitution keywordSubstitution) {
                if (keywordSubstitution == null) {
                        return null;
                }

                return keywordSubstitution.toString();
        }

        // Fields =================================================================

        private final String value;

        // Setup ==================================================================

        private KeywordSubstitution(@NonNls String value) {
                this.value = value;
        }

        // Implemented ============================================================

        public String toString() {
                return value;
        }
}