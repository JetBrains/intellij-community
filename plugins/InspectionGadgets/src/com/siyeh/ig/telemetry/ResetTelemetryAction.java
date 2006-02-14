/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.telemetry;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.siyeh.InspectionGadgetsBundle;

import javax.swing.Icon;

class ResetTelemetryAction extends AnAction{
    
    private final InspectionGadgetsTelemetry telemetry;
    private final TelemetryDisplay display;

    private static final Icon resetIcon =
            IconHelper.getIcon("/actions/reset.png");

    ResetTelemetryAction(InspectionGadgetsTelemetry telemetry,
                                TelemetryDisplay display){
        super(CommonBundle.message("button.reset"),
                InspectionGadgetsBundle.message(
                        "action.reset.telemetry.description"),
                resetIcon);
        this.telemetry = telemetry;
        this.display = display;
    }

    public void actionPerformed(AnActionEvent event){
        telemetry.reset();
        display.update();
    }
}