/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers, Mark Scott
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.portability;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.portability.mediatype.*;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HardcodedFileSeparatorsInspection extends ExpressionInspection{

    private static final char BACKSLASH = '\\';
    private static final char SLASH = '/';
    /**
     * The regular expression pattern that matches strings which are likely to
     * be date formats. <code>Pattern</font></b> instances are immutable, so
     * caching the pattern like this is still thread-safe.
     */
    @NonNls private static final Pattern DATE_FORMAT_PATTERN =
            Pattern.compile("\\b[dDmM]+/[dDmM]+(/[yY]+)?");
    /**
     * A regular expression that matches strings which represent example MIME
     * media types.
     */
    @NonNls private static final String EXAMPLE_MIME_MEDIA_TYPE_PATTERN =
            "example/\\p{Alnum}+(?:[\\.\\-\\\\+]\\p{Alnum}+)*";
    /**
     * A regular expression pattern that matches strings which start with a URL
     * protocol, as they're likely to actually be URLs.
     */
    @NonNls private static final Pattern URL_PATTERN =
            Pattern.compile("^[a-z][a-z0-9+\\-.]+://.*$");

    /**
     * All mimetypes, see http://www.iana.org/assignments/media-types/
     */
    private static final Set<String> mimeTypes = new HashSet();
    static {
        for (ImageMediaType imageMediaType : ImageMediaType.values()){
            mimeTypes.add(imageMediaType.toString());
        }
        for (ApplicationMediaType applicationMediaType :
                ApplicationMediaType.values()){
            mimeTypes.add(applicationMediaType.toString());
        }
        for (AudioMediaType audioMediaType : AudioMediaType.values()){
            mimeTypes.add(audioMediaType.toString());
        }
        for (MessageMediaType messageMediaType : MessageMediaType.values()){
            mimeTypes.add(messageMediaType.toString());
        }
        for (ModelMediaType modelMediaType : ModelMediaType.values()){
            mimeTypes.add(modelMediaType.toString());
        }
        for (MultipartMediaType multipartMediaType :
                MultipartMediaType.values()){
            mimeTypes.add(multipartMediaType.toString());
        }
        for (TextMediaType textMediaType : TextMediaType.values()){
            mimeTypes.add(textMediaType.toString());
        }
        for (VideoMediaType videoContentTypeMediaType :
                VideoMediaType.values()){
            mimeTypes.add(videoContentTypeMediaType.toString());
        }
    }

    /**
     * All {@link TimeZone} IDs.
     */
    private static final Set<String> timeZoneIds = new HashSet();
    static {
        timeZoneIds.addAll(Arrays.asList(TimeZone.getAvailableIDs()));
    }

    /**
     * @noinspection PublicField
     */
    public boolean m_recognizeExampleMediaType = false;

    @NotNull
    public String getID(){
        return "HardcodedFileSeparator";
    }

    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "hardcoded.file.separator.display.name");
    }

    @NotNull
    public String getGroupDisplayName(){
        return GroupNames.PORTABILITY_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "hardcoded.file.separator.problem.descriptor");
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(
                InspectionGadgetsBundle.message(
                        "hardcoded.file.separator.include.option"),
                this, "m_recognizeExampleMediaType");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new HardcodedFileSeparatorsVisitor();
    }

    private class HardcodedFileSeparatorsVisitor
            extends BaseInspectionVisitor{

        public void visitLiteralExpression(
                @NotNull PsiLiteralExpression expression){
            super.visitLiteralExpression(expression);
            final PsiType type = expression.getType();
            if(TypeUtils.isJavaLangString(type)){
                final String value = (String) expression.getValue();
                if (!isHardcodedFilenameString(value)) {
                    return;
                }
                final PsiElement parent = expression.getParent();
                final PsiElement grandParent = parent.getParent();
                if (grandParent instanceof PsiMethodCallExpression) {
                    final PsiMethodCallExpression methodCallExpression =
                            (PsiMethodCallExpression)grandParent;
                    if (MethodCallUtils.isCallToRegexMethod(
                            methodCallExpression)) {
                        return;
                    }
                }
                registerError(expression);
            } else if(type != null && type.equals(PsiType.CHAR)){
                final Character value = (Character) expression.getValue();
                if(value == null){
                    return;
                }
                final char unboxedValue = value.charValue();
                if(unboxedValue == BACKSLASH || unboxedValue == SLASH){
                    registerError(expression);
                }
            }
        }

        /**
         * Check whether a string is likely to be a filename containing one or more
         * hard-coded file separator characters. The method does some simple
         * analysis of the string to determine whether it's likely to be some other
         * type of data - a URL, a date format, or an XML fragment - before deciding
         * that the string is a filename.
         *
         * @param string The string to examine.
         * @return <code>true</code> if the string is likely to be a filename
         *         with hardcoded file separators, <code>false</code>
         *         otherwise.
         */
        private boolean isHardcodedFilenameString(String string){
            if(string == null){
                return false;
            }
            if(string.indexOf((int) '/') == -1 &&
                    string.indexOf((int) '\\') == -1){
                return false;
            }
            final char startChar = string.charAt(0);
            if(Character.isLetter(startChar) && string.charAt(1) == ':'){
                return true;
            }
            if(isXMLString(string)){
                return false;
            }
            if(isDateFormatString(string)){
                return false;
            }
            if(isURLString(string)){
                return false;
            }
            if(isMediaTypeString(string)){
                return false;
            }
            return !isTimeZoneIdString(string);
        }

        /**
         * Check whether a string containing at least one '/' or '\' character is
         * likely to be a fragment of XML.
         *
         * @param string The string to examine.
         * @return <code>true</code> if the string is likely to be an XML
         *         fragment, or <code>false</code> if not.
         */
        private boolean isXMLString(String string){
            return string.contains("</") || string.contains("/>");
        }

        /**
         * Check whether a string containing at least one '/' or '\' character is
         * likely to be a date format string.
         *
         * @param string The string to check.
         * @return <code>true</code> if the string is likely to be a date
         *         string, <code>false</code> if not.
         */
        private boolean isDateFormatString(String string){
            if(string.length() < 3){
                // A string this short is very unlikely to be a date format.
                return false;
            }
            final int strLength = string.length();
            final char startChar = string.charAt(0);
            final char endChar = string.charAt(strLength - 1);
            if(startChar == '/' || endChar == '/'){
                // Most likely it's a filename if the string starts or ends
                // with a slash.
                return false;
            } else if(Character.isLetter(startChar) && string.charAt(1) == ':'){
                // Most likely this is a Windows-style full file name.
                return false;
            }
            final Matcher dateFormatMatcher = DATE_FORMAT_PATTERN.matcher(string);
            return dateFormatMatcher.find();
        }

        /**
         * Checks whether a string containing at least one '/' or '\' character is
         * likely to be a URL.
         *
         * @param string The string to check.
         * @return <code>true</code> if the string is likely to be a URL,
         *         <code>false</code> if not.
         */
        private boolean isURLString(String string){
            final Matcher urlMatcher = URL_PATTERN.matcher(string);
            return urlMatcher.find();
        }

        /**
         * Checks whether a string containing at least one '/' character is
         * likely to be a MIME media type.  See the
         * <a href="http://www.iana.org/assignments/media-types/">IANA</a>
         * documents for registered MIME media types.
         *
         * @param string The string to check.
         * @return <code>true</code> if the string is likely to be a MIME
         *         media type, <code>false</code> if not.
         */
        private boolean isMediaTypeString(String string){
            // IANA doesn't specify a pattern for the subtype of example content
            // types but other subtypes seem to be one or more groups of
            // alphanumerics characters, the groups being separated by a single
            // period (.), hyphen (-) or plus (+) character
            //
            // Valid examples:
            //
            // "example/foo"
            // "example/foo.bar"
            // "example/foo-bar+baz"
            // "example/foo1.2006-bar"
            //
            // Invalid examples:
            //
            // "example/foo$bar"              ($ isn't a valid separator)
            // "example/foo."                 (can't end with a separator)
            //
            if(m_recognizeExampleMediaType &&
                    string.matches(EXAMPLE_MIME_MEDIA_TYPE_PATTERN)){
                return true;
            }
            return mimeTypes.contains(string);
        }

        /**
         * Checks whether a string containing at least one '/' character is
         * likely to be a {@link TimeZone} ID.
         *
         * @param string The string to check.
         * @return <code>true</code> if the string is likely to be a
         *         TimeZone ID, <code>false</code> if not.
         */
        private boolean isTimeZoneIdString(String string){
            return timeZoneIds.contains(string);
        }
    }
}