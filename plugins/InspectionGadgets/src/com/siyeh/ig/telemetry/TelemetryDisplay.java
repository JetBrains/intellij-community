package com.siyeh.ig.telemetry;

import javax.swing.*;


public interface TelemetryDisplay{

    JComponent getContentPane();

    void update();

}
