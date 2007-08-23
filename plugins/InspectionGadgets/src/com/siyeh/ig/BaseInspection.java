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
package com.siyeh.ig;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.ui.DocumentAdapter;
import com.siyeh.ig.ui.FormattedTextFieldMacFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JFormattedTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.text.Document;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BaseInspection extends LocalInspectionTool {

    private static final Logger LOG = Logger.getInstance("#com.siyeh.ig.BaseInspection");

    @NonNls private static final String INSPECTION = "Inspection";
    @NonNls private static final Map<String, String> packageGroupDisplayNameMap = new HashMap<String, String>();
    static {
        packageGroupDisplayNameMap.put("abstraction", GroupNames.ABSTRACTION_GROUP_NAME);
        packageGroupDisplayNameMap.put("assignment", GroupNames.ASSIGNMENT_GROUP_NAME);
        packageGroupDisplayNameMap.put("bitwise", GroupNames.BITWISE_GROUP_NAME);
        packageGroupDisplayNameMap.put("bugs", GroupNames.BUGS_GROUP_NAME);
        packageGroupDisplayNameMap.put("classlayout", GroupNames.CLASSLAYOUT_GROUP_NAME);
        packageGroupDisplayNameMap.put("classmetrics", GroupNames.CLASSMETRICS_GROUP_NAME);
        packageGroupDisplayNameMap.put("cloneable", GroupNames.CLONEABLE_GROUP_NAME);
        packageGroupDisplayNameMap.put("controlflow", GroupNames.CONTROL_FLOW_GROUP_NAME);
        packageGroupDisplayNameMap.put("dataflow", GroupNames.DATA_FLOW_ISSUES);
        packageGroupDisplayNameMap.put("dependency", GroupNames.DEPENDENCY_GROUP_NAME);
        packageGroupDisplayNameMap.put("encapsulation", GroupNames.ENCAPSULATION_GROUP_NAME);
        packageGroupDisplayNameMap.put("errorhandling", GroupNames.ERRORHANDLING_GROUP_NAME);
        packageGroupDisplayNameMap.put("finalization", GroupNames.FINALIZATION_GROUP_NAME);
        packageGroupDisplayNameMap.put("imports", GroupNames.IMPORTS_GROUP_NAME);
        packageGroupDisplayNameMap.put("inheritance", GroupNames.INHERITANCE_GROUP_NAME);
        packageGroupDisplayNameMap.put("initialization", GroupNames.INITIALIZATION_GROUP_NAME);
        packageGroupDisplayNameMap.put("internationalization", GroupNames.INTERNATIONALIZATION_GROUP_NAME);
        packageGroupDisplayNameMap.put("j2me", GroupNames.J2ME_GROUP_NAME);
        packageGroupDisplayNameMap.put("javabeans", GroupNames.JAVABEANS_GROUP_NAME);
        packageGroupDisplayNameMap.put("jdk", GroupNames.JDK_GROUP_NAME);
        packageGroupDisplayNameMap.put("jdk15", GroupNames.JDK15_SPECIFIC_GROUP_NAME);
        packageGroupDisplayNameMap.put("junit", GroupNames.JUNIT_GROUP_NAME);
        packageGroupDisplayNameMap.put("logging", GroupNames.LOGGING_GROUP_NAME);
        packageGroupDisplayNameMap.put("maturity", GroupNames.MATURITY_GROUP_NAME);
        packageGroupDisplayNameMap.put("memory", GroupNames.MEMORY_GROUP_NAME);
        packageGroupDisplayNameMap.put("methodmetrics", GroupNames.METHODMETRICS_GROUP_NAME);
        packageGroupDisplayNameMap.put("modularization", GroupNames.MODULARIZATION_GROUP_NAME);
        packageGroupDisplayNameMap.put("naming", GroupNames.NAMING_CONVENTIONS_GROUP_NAME);
        packageGroupDisplayNameMap.put("numeric", GroupNames.NUMERIC_GROUP_NAME);
        packageGroupDisplayNameMap.put("packaging", GroupNames.PACKAGING_GROUP_NAME);
        packageGroupDisplayNameMap.put("performance", GroupNames.PERFORMANCE_GROUP_NAME);
        packageGroupDisplayNameMap.put("portability", GroupNames.PORTABILITY_GROUP_NAME);
        packageGroupDisplayNameMap.put("resources", GroupNames.RESOURCE_GROUP_NAME);
        packageGroupDisplayNameMap.put("security", GroupNames.SECURITY_GROUP_NAME);
        packageGroupDisplayNameMap.put("serialization", GroupNames.SERIALIZATION_GROUP_NAME);
        packageGroupDisplayNameMap.put("style", GroupNames.STYLE_GROUP_NAME);
        packageGroupDisplayNameMap.put("threading", GroupNames.THREADING_GROUP_NAME);
        packageGroupDisplayNameMap.put("visibility", GroupNames.VISIBILITY_GROUP_NAME);
    }

    private String m_shortName = null;

    @NotNull
    public final String getShortName() {
        if (m_shortName == null) {
            final Class<? extends BaseInspection> aClass = getClass();
            final String name = aClass.getName();
            assert name.endsWith(INSPECTION) :
                    "class name must end with 'Inspection' to correctly" +
                            " calculate the short name: " + name;
            m_shortName = name.substring(name.lastIndexOf((int)'.') + 1,
                    name.length() - INSPECTION.length());
        }
        return m_shortName;
    }


    @Nls @NotNull
    public final String getGroupDisplayName() {
        final Class<? extends BaseInspection> thisClass = getClass();
        final Package thisPackage = thisClass.getPackage();
        assert thisPackage != null : "need package to determine group display name";
        final String name = thisPackage.getName();
        assert name != null :
                "inspection has default package, group display name cannot be determined";
        final int index = name.lastIndexOf('.');
        final String key = name.substring(index + 1);
        final String groupDisplayName = packageGroupDisplayNameMap.get(key);
        assert groupDisplayName != null : "No display name found for " + key;
        return groupDisplayName;
    }

    @NotNull
    protected abstract String buildErrorString(Object... infos);

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return false;
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return null;
    }

    @Nullable
    protected InspectionGadgetsFix[] buildFixes(PsiElement location) {
        return null;
    }

    public boolean hasQuickFix() {
        final Class<? extends BaseInspection> aClass = getClass();
        final Method[] methods = aClass.getDeclaredMethods();
        for (final Method method : methods) {
            @NonNls final String methodName = method.getName();
            if ("buildFix".equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    public abstract BaseInspectionVisitor buildVisitor();

    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                          boolean isOnTheFly) {
        final BaseInspectionVisitor visitor = buildVisitor();
        visitor.setProblemsHolder(holder);
        visitor.setOnTheFly(isOnTheFly);
        visitor.setInspection(this);
        return visitor;
    }


    protected JFormattedTextField prepareNumberEditor(@NonNls String fieldName) {
        try {
            final NumberFormat formatter = NumberFormat.getIntegerInstance();
            formatter.setParseIntegerOnly(true);
            final JFormattedTextField valueField = new JFormattedTextField(formatter);
            final Field field = getClass().getField(fieldName);
            valueField.setValue(field.get(this));
            valueField.setColumns(4);
            FormattedTextFieldMacFix.apply(valueField);
            final Document document = valueField.getDocument();
            document.addDocumentListener(new DocumentAdapter() {
                public void textChanged(DocumentEvent evt) {
                    try {
                        valueField.commitEdit();
                        field.set(BaseInspection.this, ((Number) valueField.getValue()).intValue());
                    } catch (IllegalAccessException e) {
                        LOG.error(e);
                    } catch (ParseException e) {
                        // No luck this time. Will update the field when correct value is entered.
                    }
                }
            });
            return valueField;
        } catch (NoSuchFieldException e) {
            LOG.error(e);
        } catch (IllegalAccessException e) {
            LOG.error(e);
        }
        return null;
    }

    protected static void parseString(String string, List<String>... outs){
        final String[] strings = string.split(",");
        for (List<String> out : outs) {
            out.clear();
        }
        for (int i = 0; i < strings.length; i += outs.length) {
            for (int j = 0; j < outs.length; j++) {
                final List<String> out = outs[j];
                out.add(strings[i + j]);
            }
        }
    }

    protected static String formatString(List<String>... strings){
        final StringBuilder buffer = new StringBuilder();
        final int size = strings[0].size();
        if (size > 0) {
            formatString(strings, 0, buffer);
            for (int i = 1; i < size; i++) {
                buffer.append(',');
                formatString(strings, i, buffer);
            }
        }
        return buffer.toString();
    }

    private static void formatString(List<String>[] strings, int index,
                                     StringBuilder out) {
        out.append(strings[0].get(index));
        for (int i = 1; i < strings.length; i++) {
            out.append(',');
            out.append(strings[i].get(index));
        }
    }
}