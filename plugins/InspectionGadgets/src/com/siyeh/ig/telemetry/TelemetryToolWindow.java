package com.siyeh.ig.telemetry;


public interface TelemetryToolWindow{
    String CYCLE_TOOL_WINDOW_ID = "IG Telemetry";

    void register();

    void show();

    void close();

    void unregister();
}
