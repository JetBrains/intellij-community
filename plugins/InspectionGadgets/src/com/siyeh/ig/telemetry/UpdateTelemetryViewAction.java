package com.siyeh.ig.telemetry;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

import javax.swing.*;

class UpdateTelemetryViewAction extends AnAction{
    private final TelemetryDisplay telemetryDisplay;

    private static final Icon refreshIcon =
            IconHelper.getIcon("/actions/sync.png");

    public UpdateTelemetryViewAction(TelemetryDisplay telemetryDisplay){
        super("Refresh", "Refresh telemetry display", refreshIcon);
        this.telemetryDisplay = telemetryDisplay;
    }

    public void actionPerformed(AnActionEvent event){
        telemetryDisplay.update();
    }
}
