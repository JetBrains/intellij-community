package com.siyeh.ig.classmetrics;

import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.ui.SingleIntegerFieldOptionsPanel;

import javax.swing.*;

public abstract class ClassMetricInspection extends ClassInspection {
    public int m_limit = getDefaultLimit();  //this is public for the DefaultJDOMSericalization thingy

    protected abstract int getDefaultLimit();

    protected abstract String getConfigurationLabel();

    protected int getLimit() {
        return m_limit;
    }

    public JComponent createOptionsPanel() {
        return new SingleIntegerFieldOptionsPanel(getConfigurationLabel(),
                this, "m_limit");
    }

}
