/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.ui.DocumentAdapter;
import com.siyeh.ig.ui.FormattedTextFieldMacFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.Document;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.List;

public abstract class BaseInspection extends BaseJavaLocalInspectionTool {

    private static final Logger LOG = Logger.getInstance("#com.siyeh.ig.BaseInspection");

    @NonNls private static final String INSPECTION = "Inspection";

    private String m_shortName = null;
    private long timestamp = -1L;
    private InspectionGadgetsPlugin inspectionGadgetsPlugin = null;


    @Override @NotNull
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


    @Override @Nls @NotNull
    public final String getGroupDisplayName() {
        return GroupDisplayNameUtil.getGroupDisplayName(getClass());
    }

    @NotNull
    protected abstract String buildErrorString(Object... infos);

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return false;
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return null;
    }

    @NotNull
    protected InspectionGadgetsFix[] buildFixes(Object... infos) {
        return InspectionGadgetsFix.EMPTY_ARRAY;
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

    @Override @NotNull
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
                @Override
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
        final List<String> strings = StringUtil.split(string, ",");
        for (List<String> out : outs) {
            out.clear();
        }
        int iMax = strings.size();
        for (int i = 0; i < iMax; i += outs.length) {
            for (int j = 0; j < outs.length; j++) {
                final List<String> out = outs[j];
                if (i + j >= iMax) {
                    out.add("");
                } else {
                    out.add(strings.get(i + j));
                }
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

    @Override
    public void inspectionStarted(LocalInspectionToolSession session, boolean isOnTheFly) {
        super.inspectionStarted(session, isOnTheFly);
        if (inspectionGadgetsPlugin.isTelemetryEnabled()) {
            timestamp = System.currentTimeMillis();
        }
    }

    @Override
    public void inspectionFinished(LocalInspectionToolSession session,
                                   ProblemsHolder problemsHolder) {
        super.inspectionFinished(session, problemsHolder);
        if (inspectionGadgetsPlugin.isTelemetryEnabled()) {
            if (timestamp < 0L) {
                LOG.warn("finish reported without corresponding start");
                return;
            }
            final long end = System.currentTimeMillis();
            final String displayName = getDisplayName();
            inspectionGadgetsPlugin.getTelemetry().reportRun(displayName, end - timestamp);
            timestamp = -1L;
        }
    }

    @Override
    public void projectOpened(Project project) {
        super.projectOpened(project);
        if (inspectionGadgetsPlugin != null) {
          return;
        }
        @NonNls
        final Application application = ApplicationManager.getApplication();
        inspectionGadgetsPlugin = (InspectionGadgetsPlugin)
                application.getComponent("InspectionGadgets");
    }

    @Override
    public void projectClosed(Project project) {
        super.projectClosed(project);
        final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        if (openProjects.length == 0) {
            inspectionGadgetsPlugin = null;
        }
    }
}