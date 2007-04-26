/*
 * Copyright 2007 Bas Leijdekkers
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
package com.siyeh.ig.threading;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ui.AddAction;
import com.siyeh.ig.ui.IGTable;
import com.siyeh.ig.ui.ListWrappingTableModel;
import com.siyeh.ig.ui.RemoveAction;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.util.ArrayList;
import java.util.List;

public class AccessToNonThreadSafeStaticFieldFromInstanceInspection
        extends BaseInspection {

    @SuppressWarnings({"PublicField"})
    public String nonThreadSafeTypes = "java.text.SimpleDateFormat," +
            "java.util.Calendar";
    List<String> nonThreadSafeTypeList = new ArrayList();

    @Nls
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "access.to.non.thread.safe.static.field.from.instance.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        if (infos[0] instanceof PsiMethod) {
            return InspectionGadgetsBundle.message(
                    "access.to.non.thread.safe.static.field.from.instance.method.problem.descriptor",
                    infos[1]);
        }
        return InspectionGadgetsBundle.message(
                "access.to.non.thread.safe.static.field.from.instance.field.problem.descriptor",
                infos[1]);
    }

    @Nullable
    public JComponent createOptionsPanel() {
        final Form form = new Form();
        return form.getContentPanel();
    }

    public void readSettings(Element node) throws InvalidDataException {
        super.readSettings(node);
        parseString(nonThreadSafeTypes, nonThreadSafeTypeList);
    }

    public void writeSettings(Element node) throws WriteExternalException {
        nonThreadSafeTypes = formatString(nonThreadSafeTypeList);
        super.writeSettings(node);
    }

    public BaseInspectionVisitor buildVisitor() {
        return new AccessToNonThreadSafeStaticFieldFromInstanceVisitor();
    }

    private class AccessToNonThreadSafeStaticFieldFromInstanceVisitor
            extends BaseInspectionVisitor {

        public void visitReferenceExpression(
                PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            final PsiModifierListOwner parent =
                    PsiTreeUtil.getParentOfType(expression,
                            PsiField.class, PsiMethod.class);
            if (parent == null) {
                return;
            }
            if (parent.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            if (parent instanceof PsiMethod) {
                final PsiMethod method = (PsiMethod) parent;
                if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
                    return;
                }
                final PsiSynchronizedStatement synchronizedStatement =
                        PsiTreeUtil.getParentOfType(expression,
                                PsiSynchronizedStatement.class);
                if (synchronizedStatement != null) {
                    return;
                }
            }
            final PsiExpression qualifier = expression.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            final PsiType type = qualifier.getType();
            if (type == null) {
                return;
            }
            String typeString = null;
            for (String nonThreadSafeType : nonThreadSafeTypeList) {
                if (type.equalsToText(nonThreadSafeType)) {
                   typeString = nonThreadSafeType;
                    break;
                }
            }
            final PsiElement referenceParent = expression.getParent();
            if (referenceParent instanceof PsiMethodCallExpression) {
                registerError(referenceParent, parent, typeString);
            } else {
                registerError(expression, parent, typeString);
            }
        }
    }

    private class Form {

        private IGTable table;
        private JButton addButton;
        private JButton removeButton;
        private JPanel contentPanel;

        Form() {
            super();
            addButton.setAction(new AddAction(table));
            removeButton.setAction(new RemoveAction(table));
        }

        private void createUIComponents() {
            table = new IGTable(
                    new ListWrappingTableModel(nonThreadSafeTypeList,
                            InspectionGadgetsBundle.message(
                                    "non.thread.safe.types.column.name")));
        }

        public JComponent getContentPanel() {
            return contentPanel;
        }
    }
}