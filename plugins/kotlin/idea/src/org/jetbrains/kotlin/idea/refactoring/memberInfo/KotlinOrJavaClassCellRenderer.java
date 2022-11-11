// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.memberInfo;

import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiNamedElement;
import com.intellij.ui.ListCellRendererWrapper;

import javax.swing.*;

public class KotlinOrJavaClassCellRenderer extends ListCellRendererWrapper<PsiNamedElement> {
    @Override
    public void customize(JList list, PsiNamedElement value, int index, boolean selected, boolean hasFocus) {
        if (value == null) return;

        setText(MemberInfoUtilsKt.qualifiedClassNameForRendering(value));
        Icon icon = value.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS);
        if (icon != null) {
            setIcon(icon);
        }
    }
}
