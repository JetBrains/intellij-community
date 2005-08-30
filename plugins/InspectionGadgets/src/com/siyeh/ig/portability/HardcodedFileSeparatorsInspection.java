/*
 * Copyright 2003-2005 Dave Griffith
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
import com.intellij.psi.PsiType;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

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
         * A regular expression pattern that matches strings which start with a URL
         * protocol, as they're likely to actually be URLs.
         */
    @NonNls private static final Pattern URL_PATTERN =
            Pattern.compile("^[a-z][a-z0-9+\\-.]+://.*$");

    public String getID(){
        return "HardcodedFileSeparator";
    }

    public String getDisplayName(){
        return InspectionGadgetsBundle.message("hardcoded.file.separator.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.PORTABILITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return InspectionGadgetsBundle.message("hardcoded.file.separator.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new HardcodedFileSeparatorsVisitor();
    }

    /**
         * Check whether a string is likely to be a filename containing one or more
         * hard-coded file separator characters. The method does some simple
         * analysis of the string to determine whether it's likely to be some other
         * type of data - a URL, a date format, or an XML fragment - before deciding
         * that the string is a filename.
         *
         * @param str The string to examine.
         * @return <code>true</font></b> if the string is likely to be a filename
         *         with hardcoded file separators, <code>false</font></b>
         *         otherwise.
         */
    private static boolean isHardcodedFilenameString(String str){
        if(str == null){
            return false;
        }
        if(str.indexOf((int) '/') == -1 && str.indexOf((int) '\\') == -1){
            return false;
        }

        final char startChar = str.charAt(0);
        if(Character.isLetter(startChar) && str.charAt(1) == ':'){
            return true;
        }

        if(isXMLString(str)){
            return false;
        }

        if(isDateFormatString(str)){
            return false;
        }

        return !isURLString(str);

    }

    /**
         * Check whether a string containing at least one '/' or '\' character is
         * likely to be a fragment of XML.
         *
         * @param str The string to examine.
         * @return <code>true</font></b> if the string is likely to be an XML
         *         fragment, or <code>false</font></b> if not.
         */
    private static boolean isXMLString(String str){
        if(str.indexOf("</") != -1){
            return true;
        }

        return str.indexOf("/>") != -1;

    }

    /**
         * Check whether a string containing at least one '/' or '\' character is
         * likely to be a date format string.
         *
         * @param str The string to check.
         * @return <code>true</font></b> if the string is likely to be a date
         *         string, <code>false</font></b> if not.
         */
    private static boolean isDateFormatString(String str){
        if(str.length() < 3){
            // A string this short is very unlikely to be a date format.
            return false;
        }
        final int strLength = str.length();
        final char startChar = str.charAt(0);
        final char endChar = str.charAt(strLength - 1);
        if(startChar == '/' || endChar == '/'){
            // Most likely it's a filename if the string starts or ends with a slash.
            return false;
        } else if(Character.isLetter(startChar) && str.charAt(1) == ':'){
            // Most likely this is a Windows-style full file name.
            return false;
        }

        final Matcher dateFormatMatcher = DATE_FORMAT_PATTERN.matcher(str);
        return dateFormatMatcher.find();
    }

    /**
         * Checks whether a string containing at least one '/' or '\' character is
         * likely to be a URL.
         *
         * @param str The string to check.
         * @return <code>true</font></b> if the string is likely to be a URL,
         *         <code>false</font></b> if not.
         */
    private static boolean isURLString(String str){
        final Matcher urlMatcher = URL_PATTERN.matcher(str);
        return urlMatcher.find();
    }

    private static class HardcodedFileSeparatorsVisitor
            extends BaseInspectionVisitor{

        public void visitLiteralExpression(@NotNull PsiLiteralExpression expression){
            super.visitLiteralExpression(expression);
            final PsiType type = expression.getType();
            if(TypeUtils.isJavaLangString(type)){
                final String value = (String) expression.getValue();

                if(isHardcodedFilenameString(value)){
                    registerError(expression);
                }
            } else if(type.equals(PsiType.CHAR)){
                final Character value = (Character) expression.getValue();
                if(value == null){
                    return;
                }
                if(value == BACKSLASH || value == SLASH){
                    registerError(expression);
                }
            }
        }
    }
}
