package com.siyeh.ig.classmetrics;

import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.ui.SingleIntegerFieldOptionsPanel;

import javax.swing.*;

public abstract class ClassMetricInspection extends ClassInspection {
    /** @noinspection PublicField*/
    public int m_limit = getDefaultLimit();

    protected abstract int getDefaultLimit();

    protected abstract String getConfigurationLabel();

    protected int getLimit() {
        return m_limit;
    }

    public JComponent createOptionsPanel() {
        final String label = getConfigurationLabel();
        return new SingleIntegerFieldOptionsPanel(label,
                this, "m_limit");
    }

}
