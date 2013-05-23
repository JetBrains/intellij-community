package org.hanuna.gitalk.swing_ui;

import org.hanuna.gitalk.swing_ui.frame.ErrorModalDialog;
import org.hanuna.gitalk.swing_ui.frame.MainFrame;
import org.hanuna.gitalk.swing_ui.frame.ProgressFrame;
import org.hanuna.gitalk.ui.ControllerListener;
import org.hanuna.gitalk.ui.UI_Controller;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author erokhins
 */
public class Swing_UI {
    private final ErrorModalDialog errorFrame = new ErrorModalDialog("error");
    private final ProgressFrame progressFrame = new ProgressFrame("Begin load");
    private final ControllerListener swingControllerListener = new SwingControllerListener();
    private final UI_Controller ui_controller;
    private MainFrame mainFrame = null;

    public Swing_UI(UI_Controller ui_controller) {
        this.ui_controller = ui_controller;
    }


    public ControllerListener getControllerListener() {
        return swingControllerListener;
    }

    public void setState(ControllerListener.State state) {
        System.out.println(state);
        switch (state) {
            case USUAL:
                if (mainFrame == null) {
                    mainFrame = new MainFrame(ui_controller);
                }
                mainFrame.setVisible(true);
                errorFrame.setVisible(false);
                progressFrame.setVisible(false);
                break;
            case ERROR:
                errorFrame.setVisible(true);
                break;
            case PROGRESS:
                progressFrame.setVisible(true);
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    private class SwingControllerListener implements ControllerListener {

        @Override
        public void jumpToRow(final int rowIndex) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    mainFrame.getGraphTable().jumpToRow(rowIndex);
                }
            });
        }

        @Override
        public void updateUI() {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    mainFrame.getGraphTable().updateUI();
                }
            });
        }

        @Override
        public void setState(@NotNull final State state) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    Swing_UI.this.setState(state);
                }
            });
        }

        @Override
        public void setErrorMessage(@NotNull final String errorMessage) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    errorFrame.setMessage(errorMessage);
                }
            });
        }

        @Override
        public void setUpdateProgressMessage(@NotNull final String progressMessage) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    progressFrame.setMessage(progressMessage);
                }
            });
        }
    }
}
