/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.ui.AddAction;
import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.codeInspection.ui.RemoveAction;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.ui.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class IgnoreResultOfCallInspection extends BaseInspection {

    /** @noinspection PublicField*/
    public boolean m_reportAllNonLibraryCalls = false;

    /** @noinspection PublicField*/
    @NonNls public String callCheckString = "java.io.InputStream,read," +
            "java.io.InputStream,skip," +
            "java.lang.StringBuffer,toString," +
            "java.lang.StringBuilder,toString," +
            "java.lang.String,.*," +
            "java.math.BigInteger,.*," +
            "java.math.BigDecimal,.*," +
            "java.net.InetAddress,.*," +
            "java.io.File,.*," +
            "java.lang.Object,equals|hashCode";

    final List<String> methodNamePatterns = new ArrayList();
    final List<String> classNames = new ArrayList();
    Map<String, Pattern> patternCache = null;

    public IgnoreResultOfCallInspection(){
        parseString(callCheckString, classNames, methodNamePatterns);
    }

    @Override
    @NotNull
    public String getID(){
        return "ResultOfMethodCallIgnored";
    }

    @Override
    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "result.of.method.call.ignored.display.name");
    }

    @Override
    @NotNull
    public String buildErrorString(Object... infos){
        final PsiClass containingClass = (PsiClass)infos[0];
        final String className = containingClass.getName();
        return InspectionGadgetsBundle.message(
                "result.of.method.call.ignored.problem.descriptor",
                className);
    }

    @Override
    public void readSettings(Element element) throws InvalidDataException{
        super.readSettings(element);
        parseString(callCheckString, classNames, methodNamePatterns);
    }

    @Override
    public void writeSettings(Element element) throws WriteExternalException{
        callCheckString = formatString(classNames, methodNamePatterns);
        super.writeSettings(element);
    }

    @Override
    public JComponent createOptionsPanel(){
        final Form form = new Form();
        return form.getContentPanel();
    }

    @Override
    public boolean isEnabledByDefault(){
        return true;
    }

    @Override
    public BaseInspectionVisitor buildVisitor(){
        return new IgnoreResultOfCallVisitor();
    }

    private class IgnoreResultOfCallVisitor extends BaseInspectionVisitor{

        @Override public void visitExpressionStatement(
                @NotNull PsiExpressionStatement statement){
            super.visitExpressionStatement(statement);
            final PsiExpression expression = statement.getExpression();
            if(!(expression instanceof PsiMethodCallExpression)){
                return;
            }
            final PsiMethodCallExpression call =
                    (PsiMethodCallExpression) expression;
            final PsiMethod method = call.resolveMethod();
            if (method == null || method.isConstructor()) {
                return;
            }
            final PsiType returnType = method.getReturnType();
            if(PsiType.VOID.equals(returnType)){
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if(aClass == null){
                return;
            }
            if(m_reportAllNonLibraryCalls &&
                       !LibraryUtil.classIsInLibrary(aClass)){
                registerMethodCallError(call, aClass);
                return;
            }
            final PsiReferenceExpression methodExpression =
                    call.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if(methodName == null){
                return;
            }
            for (int i = 0; i < methodNamePatterns.size(); i++) {
                final String methodNamePattern = methodNamePatterns.get(i);
                if (!methodNamesMatch(methodName, methodNamePattern)) {
                    continue;
                }
                final String className = classNames.get(i);
                if (!ClassUtils.isSubclass(aClass, className)) {
                    continue;
                }
                registerMethodCallError(call, aClass);
                return;
            }
        }

        private boolean methodNamesMatch(String methodName,
                                         String methodNamePattern){
            Pattern pattern;
            if (patternCache != null) {
                pattern = patternCache.get(methodNamePattern);
            } else {
                patternCache = new HashMap(methodNamePatterns.size());
                pattern = null;
            }
            if (pattern == null) {
                try {
                    pattern = Pattern.compile(methodNamePattern);
                    patternCache.put(methodNamePattern, pattern);
                } catch (PatternSyntaxException ignore) {
                    return false;
                } catch (NullPointerException ignore) {
                    return false;
                }
            }
            if (pattern == null) {
                return false;
            }
            final Matcher matcher = pattern.matcher(methodName);
            return matcher.matches();
        }
    }

    private class Form{

        JPanel contentPanel;
        JButton addButton;
        JButton removeButton;
        ListTable table;
        JCheckBox nonLibraryCheckbox;

        Form(){
            super();
            addButton.setAction(new AddAction(table));
            removeButton.setAction(new RemoveAction(table));
            nonLibraryCheckbox.setAction(new ToggleAction(
                    InspectionGadgetsBundle.message(
                            "result.of.method.call.ignored.non.library.option"),
                    IgnoreResultOfCallInspection.this,
                    "m_reportAllNonLibraryCalls"));
            nonLibraryCheckbox.setSelected(m_reportAllNonLibraryCalls);
        }

        private void createUIComponents() {
            table = new ListTable(new ListWrappingTableModel(
                    Arrays.asList(classNames, methodNamePatterns),
                    InspectionGadgetsBundle.message(
                            "result.of.method.call.ignored.class.column.title"),
                    InspectionGadgetsBundle.message(
                            "result.of.method.call.ignored.method.column.title")));
        }

        public JComponent getContentPanel(){
            return contentPanel;
        }
    }
}