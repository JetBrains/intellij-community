// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.pullUp;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.memberPullUp.PullUpDialogBase;
import com.intellij.refactoring.ui.AbstractMemberSelectionTable;
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo;
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfoStorage;
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinOrJavaClassCellRenderer;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtNamedDeclaration;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

// TODO: This is workaround which allows KotlinPullUpDialog to be compiled against both Java 6 and 8
public abstract class KotlinPullUpDialogBase extends
                                    PullUpDialogBase<KotlinMemberInfoStorage, KotlinMemberInfo, KtNamedDeclaration, PsiNamedElement> {
    protected KotlinPullUpDialogBase(
            Project project,
            KtClassOrObject object,
            List<PsiNamedElement> superClasses,
            KotlinMemberInfoStorage memberInfoStorage,
            @NlsContexts.DialogTitle String title
    ) {
        super(project, object, superClasses, memberInfoStorage, title);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void initClassCombo(JComboBox classCombo) {
        classCombo.setRenderer(new KotlinOrJavaClassCellRenderer());
        classCombo.addItemListener(
                new ItemListener() {
                    @Override
                    public void itemStateChanged(ItemEvent e) {
                        if (e.getStateChange() == ItemEvent.SELECTED) {
                            if (myMemberSelectionPanel == null) return;
                            AbstractMemberSelectionTable<KtNamedDeclaration, KotlinMemberInfo> table = myMemberSelectionPanel.getTable();
                            if (table == null) return;
                            table.setMemberInfos(myMemberInfos);
                            table.fireExternalDataChange();
                        }
                    }
                }
        );
    }
}
