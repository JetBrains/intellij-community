package org.hanuna.gitalk.main;

import org.hanuna.gitalk.swing_ui.Swing_UI;
import org.hanuna.gitalk.ui.impl.UI_ControllerImpl;

/**
 * @author erokhins
 */
public class Main {
    public static void main(String[] args) {
        UI_ControllerImpl ui_controller = new UI_ControllerImpl();
        Swing_UI swing_ui = new Swing_UI(ui_controller);
        ui_controller.addControllerListener(swing_ui.getControllerListener());
        ui_controller.init(true);
    }
}
