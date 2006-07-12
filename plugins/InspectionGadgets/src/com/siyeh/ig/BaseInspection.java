/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

public abstract class BaseInspection extends LocalInspectionTool {

    private String m_shortName = null;
    private InspectionRunListener listener = null;
    private boolean telemetryEnabled = true;
    @NonNls private static final String INSPECTION = "Inspection";
    @NonNls private static final String INSPECTION_GADGETS_COMPONENT_NAME =
            "InspectionGadgets";

    public String getShortName() {
        if (m_shortName == null) {
            final Class<? extends BaseInspection> aClass = getClass();
            final String name = aClass.getName();
            m_shortName = name.substring(name.lastIndexOf((int)'.') + 1,
                    name.length() - INSPECTION.length());
        }
        return m_shortName;
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

    private void initializeTelemetryIfNecessary() {
        if (telemetryEnabled && listener == null) {
            final Application application = ApplicationManager.getApplication();
            final InspectionGadgetsPlugin plugin = (InspectionGadgetsPlugin)
                    application.getComponent(INSPECTION_GADGETS_COMPONENT_NAME);
            telemetryEnabled = InspectionGadgetsPlugin.isTelemetryEnabled();
            listener = plugin.getTelemetry();
        }
    }

    private String getPropertyPrefixForInspection() {
        final String shortName = getShortName();
        return getPrefix(shortName);
    }

    public static String getPrefix(String shortName) {
        final int length = shortName.length();
        final StringBuilder builder = new StringBuilder(length + 10);
        builder.append(Character.toLowerCase(shortName.charAt(0)));
        for (int i = 1; i < length; i++) {
            final char c = shortName.charAt(i);
            if (Character.isUpperCase(c)) {
                builder.append('.');
                builder.append(Character.toLowerCase(c));
            }
            else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    public String getDisplayName() {
        @NonNls final String displayNameSuffix = ".display.name";
        return InspectionGadgetsBundle.message(
                getPropertyPrefixForInspection() + displayNameSuffix);
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

    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly) {
        final BaseInspectionVisitor visitor = buildVisitor();
        visitor.setProblemsHolder(holder);
        visitor.setOnTheFly(isOnTheFly);
        visitor.setInspection(this);
        return visitor;
    }
}