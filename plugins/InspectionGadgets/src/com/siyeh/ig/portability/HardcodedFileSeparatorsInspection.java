package com.siyeh.ig.portability;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.TypeUtils;

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
    private static final Pattern DATE_FORMAT_PATTERN =
            Pattern.compile("\\b[dDmM]+/[dDmM]+(/[yY]+)?");
    /**
         * A regular expression pattern that matches strings which start with a URL
         * protocol, as they're likely to actually be URLs.
         */
    private static final Pattern URL_PATTERN =
            Pattern.compile("^[a-z][a-z0-9+\\-.]+://.*$");

    public String getID(){
        return "HardcodedFileSeparator";
    }

    public String getDisplayName(){
        return "Hardcoded file separator";
    }

    public String getGroupDisplayName(){
        return GroupNames.PORTABILITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Hardcoded file separator #ref #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new HardcodedFileSeparatorsVisitor(this, inspectionManager,
                                                  onTheFly);
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
        private HardcodedFileSeparatorsVisitor(BaseInspection inspection,
                                               InspectionManager inspectionManager,
                                               boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitLiteralExpression(PsiLiteralExpression expression){
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
                final char charValue = value;
                if(charValue == BACKSLASH || charValue == SLASH){
                    registerError(expression);
                }
            }
        }
    }
}
