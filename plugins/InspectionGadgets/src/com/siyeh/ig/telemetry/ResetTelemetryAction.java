package com.siyeh.ig.telemetry;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

import javax.swing.*;

class ResetTelemetryAction extends AnAction{
    private final InspectionGadgetsTelemetry telemetry;
    private final TelemetryDisplay display;

    private static final Icon resetIcon =
            IconHelper.getIcon("/actions/reset.png");
    public ResetTelemetryAction(InspectionGadgetsTelemetry telemetry,
                                TelemetryDisplay display){
        super("Reset" , "Reset telemetry data", resetIcon);
        this.telemetry = telemetry;
        this.display = display;
    }

    public void actionPerformed(AnActionEvent event){
        telemetry.reset();
        display.update();
    }
}
