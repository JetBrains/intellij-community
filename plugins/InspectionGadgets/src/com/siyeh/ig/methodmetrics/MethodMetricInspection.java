package com.siyeh.ig.methodmetrics;

import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.ui.SingleIntegerFieldOptionsPanel;

import javax.swing.*;

public abstract class MethodMetricInspection extends MethodInspection {
    public int m_limit = getDefaultLimit();  //this is public for the DefaultJDOMSerialization thingy

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
